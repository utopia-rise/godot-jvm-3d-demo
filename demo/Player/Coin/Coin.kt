package Player.Coin

import godot.annotation.Script
import godot.annotation.Register
import godot.annotation.Visible
import godot.api.Area3D
import godot.api.AudioStreamPlayer3D
import godot.api.Node3D
import godot.api.PhysicsBody3D
import godot.api.PhysicsServer3D
import godot.api.RigidBody3D
import godot.core.Vector3
import godot.core.asStringName
import godot.core.methodCallable0
import godot.core.methodCallable1
import godot.extension.getNodeAs
import godot.extension.connectMethod
import godot.global.GD
import kotlin.random.Random

const val MIN_LAUNCH_RANGE = 2.0
const val MAX_LAUNCH_RANGE = 4.0
const val MIN_LAUNCH_HEIGHT = 1.0
const val MAX_LAUNCH_HEIGHT = 3.0
const val SPAWN_TWEEN_DURATION = 1.0
const val FOLLOW_TWEEN_DURATION = 0.5

@Script
class Coin : RigidBody3D() {

    @Visible
    lateinit var collectAudio: AudioStreamPlayer3D

    @Visible
    lateinit var playerDetectionArea: Area3D

    private var initialTweenPosition = Vector3.ZERO
    private var target: Node3D? = null

    override fun _ready() {
        collectAudio = getNodeAs("CollectAudio")!!
        playerDetectionArea = getNodeAs("PlayerDetectionArea")!!
    }

    @Register
    fun spawn() {
        val randHeight = MIN_LAUNCH_HEIGHT + (Random.nextDouble() * (MAX_LAUNCH_HEIGHT - MIN_LAUNCH_HEIGHT))
        val randDir = Vector3.FORWARD.rotated(Vector3.UP, Random.nextDouble() * (2 * Math.PI))
        val randPos = randDir * (MIN_LAUNCH_RANGE + (Random.nextDouble() * (MAX_LAUNCH_RANGE - MIN_LAUNCH_RANGE)))
        randPos.y = randHeight
        applyCentralImpulse(randPos)

        getTree()!!.createTimer(0.5).timeout.connectMethod(this, Coin::onCoinDelayTimeout)
        playerDetectionArea.bodyEntered.connectMethod(this, Coin::onBodyEntered)
    }

    fun setTarget(newTarget: PhysicsBody3D) {
        PhysicsServer3D.bodyAddCollisionException(getRid(), newTarget.getRid())
        if (target == null) {
            sleeping = true
            freeze = true

            initialTweenPosition = globalPosition
            target = newTarget

            val tween = createTween()
            tween.tweenMethod(methodCallable1(this, Coin::follow), 0.0, 1.0, FOLLOW_TWEEN_DURATION)
            tween.tweenCallback(methodCallable0(this, Coin::collect))
        }
    }

    @Register
    fun follow(offset: Float) {
        globalPosition = GD.lerp(initialTweenPosition, target!!.globalPosition, offset)
    }

    @Register
    fun collect() {
        collectAudio.pitchScale = GD.randfn(1.0f, 0.1f)
        collectAudio.play()
        target!!.call("collect_coin".asStringName())
        hide()
        collectAudio.finished.connectMethod(this, Coin::queueFree)
    }

    @Register
    fun onBodyEntered(body: Node3D) {
        if (body is PhysicsBody3D && body.hasMethod("collect_coin".asStringName())) {
            setTarget(body)
        }
    }

    @Register
    fun onCoinDelayTimeout() {
        // Delay so the player does not collect the coin the instant it spawns.
        setCollisionMaskValue(1, true)
        playerDetectionArea.setCollisionMaskValue(1, true)
    }
}
