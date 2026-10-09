package Player

import godot.annotation.Export
import godot.annotation.Script
import godot.annotation.Register
import godot.api.Area3D
import godot.api.AudioStreamPlayer3D
import godot.api.CharacterBody3D
import godot.api.Node3D
import godot.api.PackedScene
import godot.api.ProjectSettings
import godot.api.Timer
import godot.core.ConnectFlags
import godot.core.Vector3
import godot.extension.connectMethod
import godot.global.GD
import shared.Damageable

@Script
class Grenade : CharacterBody3D() {
    @Export
    lateinit var explosionScene: PackedScene

    @Export
    lateinit var explosionArea3D: Area3D

    @Export
    lateinit var explosionSound: AudioStreamPlayer3D

    @Export
    lateinit var explosionStartTimer: Timer


    private val gravity: Double by lazy {
        ProjectSettings.getSetting("physics/3d/default_gravity") as Double
    }

    override fun _ready() {
        explosionStartTimer.timeout.connectMethod(this, Grenade::explode)
    }

    override fun _physicsProcess(delta: Double) {
        velocity += Vector3.DOWN * gravity * delta
        val collision = moveAndCollide(velocity * delta)

        if (collision != null) {
            velocity = velocity.bounce(collision.getNormal(0)) * 0.7

            if (explosionStartTimer.isStopped()) {
                explosionStartTimer.start()
            }
        }
    }

    @Register
    fun `throw`(throwVelocity: Vector3) {
        velocity = throwVelocity
    }

    @Register
    fun explode() {
        setPhysicsProcess(false)

        explosionSound.pitchScale = GD.randfn(2f, 0.1f)
        explosionSound.play()

        explosionArea3D
            .getOverlappingBodies()
            .filter { body -> body !is Player }
            .filter { body -> body is Damageable }
            .forEach { body ->
                val impactPoint = (globalPosition - body.globalPosition)
                    .normalized()
                    .let { impactPoint ->
                        (impactPoint + Vector3.DOWN).normalized() * 0.5
                    }
                val force = -impactPoint * 10.0

                require(body is Damageable)
                body.damage(impactPoint, force)
            }

        explosionScene.instantiate()?.let { explosion ->
            getParent()?.addChild(explosion)
            (explosion as? Node3D)?.let { it.globalPosition = globalPosition }
        }

        hide()
        explosionSound.finished.connectMethod(this, Grenade::queueFree, ConnectFlags.ONE_SHOT)
    }
}
