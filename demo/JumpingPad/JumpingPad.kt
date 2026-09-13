package JumpingPad

import Player.Player
import godot.annotation.Export
import godot.annotation.Script
import godot.annotation.Register
import godot.api.Area3D
import godot.api.Node3D
import godot.api.Tween
import godot.core.asNodePath
import godot.extension.connectMethod

@Script
class JumpingPad : Area3D() {

    @Export
    var impulseStrength = 10.0

    @Export
    lateinit var mushroom: Node3D

    override fun _ready() {
        bodyEntered.connectMethod(this, JumpingPad::onBodyEntered)
    }

    @Register
    fun onBodyEntered(body: Node3D) {
        if (body is Player) {
            val launchDirection = mushroom.globalTransform.basis.y.normalized()
            body.velocity = launchDirection * (body.jumpInitialImpulse + impulseStrength)

            mushroom.scaleMutate { y = 0.4 }
            createTween().tweenProperty(mushroom, "scale:y".asNodePath(), 1.0, 1.0)
                .setEase(Tween.EaseType.OUT)
                .setTrans(Tween.TransitionType.ELASTIC)
        }
    }
}
