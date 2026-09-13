package Enemies

import Enemies.BeeBot.BeeRoot
import Player.Bullet
import Player.Player
import godot.annotation.Export
import godot.annotation.Script
import godot.annotation.Register
import godot.api.AnimationPlayer
import godot.api.Area3D
import godot.api.AudioStreamPlayer3D
import godot.api.CollisionShape3D
import godot.api.Node3D
import godot.api.PackedScene
import godot.api.ResourceLoader
import godot.core.Vector3
import godot.core.asStringName
import godot.extension.instantiateAs
import godot.extension.loadAs
import godot.extension.SignalConnector
import godot.extension.connectMethod

@Script
class Beebot : Enemy() {
    // Flying enemy: hovers above the ground point it is placed at.
    override val spawnHeight = 2.0


    @Export
    override var coinsCount = 7

    @Export
    var shootTimer = 1.5

    @Export
    var bulletSpeed = 6.0

    @Export
    lateinit var reactionAnimationPlayer: AnimationPlayer

    @Export
    lateinit var flyingAnimationPlayer: AnimationPlayer

    @Export
    lateinit var detectionArea: Area3D

    @Export
    lateinit var deathMeshCollider: CollisionShape3D

    @Export
    lateinit var beeRoot: BeeRoot

    @Export
    lateinit var defeatSound: AudioStreamPlayer3D

    private val bulletScene = ResourceLoader.loadAs<PackedScene>("res://demo/Player/Bullet.tscn")!!

    private val foundPlayerName = "found_player".asStringName()
    private val lostPlayerName = "lost_player".asStringName()
    private val disabledName = "disabled".asStringName()
    private lateinit var bodyEnteredConnection: SignalConnector
    private lateinit var bodyExitedConnection: SignalConnector

    var shootCount = 0.0
    var target: Node3D? = null
    var alive = true

    override fun _ready() {
        super._ready()
        bodyEnteredConnection = detectionArea.bodyEntered.connectMethod(this, Beebot::onBodyEntered)
        bodyExitedConnection = detectionArea.bodyExited.connectMethod(this, Beebot::onBodyExited)
        beeRoot.playIdle()
    }

    override fun _physicsProcess(delta: Double) {
        if (!alive) return
        target?.let {
            transform = transform.run {
                val targetTransform = lookingAt(it.globalPosition)
                interpolateWith(targetTransform, 0.1)
            }

            shootCount += delta
            if (shootCount > shootTimer) {
                beeRoot.playSpitAttack()
                shootCount -= shootTimer

                val bullet = bulletScene.instantiateAs<Bullet>()!!
                bullet.shooter = this
                val origin = globalPosition
                val target = it.globalPosition + Vector3.UP
                val aimDirection = (target - globalPosition).normalized()
                bullet.velocity = aimDirection * bulletSpeed
                bullet.distanceLimit = 14.0f
                getParent()!!.addChild(bullet)
                bullet.globalPosition = origin
            }
        }
    }

    @Register
    override fun damage(impactPoint: Vector3, velocity: Vector3) {
        applyImpulse(velocity.limitLength(3.0), impactPoint)

        if (!alive) {
            return
        }

        defeatSound.play()
        alive = false

        flyingAnimationPlayer.stop()
        flyingAnimationPlayer.seek(0.0, true)
        bodyEnteredConnection.disconnect()
        bodyExitedConnection.disconnect()
        target = null
        deathMeshCollider.setDeferred(disabledName, false)

        gravityScale = 1.0f
        beeRoot.playPoweroff()

        death()
    }

    @Register
    fun onBodyEntered(body: Node3D) {
        if (body is Player) {
            shootCount = 0.0
            target = body
            reactionAnimationPlayer.play(foundPlayerName)
        }
    }

    @Register
    fun onBodyExited(body: Node3D) {
        if (body is Player) {
            target = null
            reactionAnimationPlayer.play(lostPlayerName)
        }
    }
}
