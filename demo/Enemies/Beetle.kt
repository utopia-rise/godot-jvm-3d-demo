package Enemies

import Enemies.beetle_bot.BeetleBotSkin
import Player.Player
import godot.annotation.Export
import godot.annotation.Script
import godot.annotation.Register
import godot.api.AnimationPlayer
import godot.api.Area3D
import godot.api.AudioStreamPlayer3D
import godot.api.CollisionShape3D
import godot.api.NavigationAgent3D
import godot.api.Node3D
import godot.core.Vector3
import godot.core.asStringName
import godot.extension.SignalConnector
import godot.extension.connectMethod

@Script
class Beetle : Enemy() {
    // Ground enemy: walks the navmesh, stays where it is placed.
    override val spawnHeight = 0.0


    @Export
    override var coinsCount = 5

    @Export
    lateinit var reactionAnimationPlayer: AnimationPlayer

    @Export
    lateinit var detectionArea: Area3D

    @Export
    lateinit var beetleSkin: BeetleBotSkin

    @Export
    lateinit var navigationAgent: NavigationAgent3D

    @Export
    lateinit var deathCollisionShape: CollisionShape3D

    @Export
    lateinit var defeatSound: AudioStreamPlayer3D

    private val foundPlayerName = "found_player".asStringName()
    private val lostPlayerName = "lost_player".asStringName()
    private val disabledName = "disabled".asStringName()
    private lateinit var bodyEnteredConnection: SignalConnector
    private lateinit var bodyExitedConnection: SignalConnector

    var target: Node3D? = null
    var alive = true

    override fun _ready() {
        super._ready()
        bodyEnteredConnection = detectionArea.bodyEntered.connectMethod(this, Beetle::onBodyEntered)
        bodyExitedConnection = detectionArea.bodyExited.connectMethod(this, Beetle::onBodyExited)
        beetleSkin.idle()
    }

    override fun _physicsProcess(delta: Double) {
        if (!alive) return
        target?.let {
            beetleSkin.walk()
            val targetLookPosition = it.globalPosition
            targetLookPosition.y = globalPosition.y
            if (targetLookPosition != Vector3.ZERO) {
                lookAt(targetLookPosition)
            }

            navigationAgent.targetPosition = it.globalPosition

            val nextLocation = navigationAgent.getNextPathPosition()

            if (!navigationAgent.isTargetReached()) {
                var direction = (nextLocation - globalPosition)
                direction.y = 0.0
                direction = direction.normalized()

                val collision = moveAndCollide(direction * delta * 3)
                if (collision != null) {
                    val collider = collision.getCollider()
                    if (collider is Player) {
                        val impactPoint: Vector3 = globalPosition - collider.globalPosition
                        var force = -impactPoint
                        // Throws player up a little bit
                        force.y = 0.5
                        force *= 10.0
                        collider.damage(impactPoint, force)
                        beetleSkin.attack()
                    }
                }
            }
        }
    }

    @Register
    override fun damage(impactPoint: Vector3, velocity: Vector3) {
        lockRotation = false
        applyImpulse(velocity.limitLength(3.0), impactPoint)

        if (!alive) {
            return
        }

        defeatSound.play()
        alive = false
        beetleSkin.powerOff()

        bodyEnteredConnection.disconnect()
        bodyExitedConnection.disconnect()
        target = null
        deathCollisionShape.setDeferred(disabledName, false)

        axisLockAngularX = false
        axisLockAngularY = false
        axisLockAngularZ = false
        gravityScale = 1.0f

        death()
    }

    @Register
    fun onBodyEntered(body: Node3D) {
        if (body is Player) {
            target = body
            reactionAnimationPlayer.play(foundPlayerName)
        }
    }

    @Register
    fun onBodyExited(body: Node3D) {
        if (body is Player) {
            target = null
            reactionAnimationPlayer.play(lostPlayerName)
            beetleSkin.idle()
        }
    }
}
