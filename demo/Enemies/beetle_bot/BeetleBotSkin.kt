package Enemies.beetle_bot

import godot.annotation.Export
import godot.annotation.Script
import godot.annotation.Register
import godot.api.Animation
import godot.api.AnimationNodeStateMachinePlayback
import godot.api.AnimationPlayer
import godot.api.AnimationTree
import godot.api.Node3D
import godot.api.Timer
import godot.core.PackedStringArray
import godot.core.asStringName
import godot.global.GD

@Script
class BeetleBotSkin : Node3D() {

    @Export
    var forceLoop = PackedStringArray()

    @Export
    lateinit var animationTree: AnimationTree

    @Export
    lateinit var player: AnimationPlayer

    @Export
    lateinit var secondaryActionTimer: Timer

    private lateinit var mainStateMachine: AnimationNodeStateMachinePlayback

    private val playbackName = "parameters/StateMachine/playback".asStringName()

    private val idleName = "Idle".asStringName()
    private val walkName = "Walk".asStringName()
    private val shakeName = "Shake".asStringName()
    private val attackName = "Attack".asStringName()
    private val powerOffName = "PowerOff".asStringName()

    override fun _ready() {
        animationTree.active = true
        mainStateMachine = animationTree.get(playbackName) as AnimationNodeStateMachinePlayback

        forceLoop.forEach { animationName ->
            val anim = player.getAnimation(animationName.asStringName())!!
            anim.loopMode = Animation.LoopMode.LINEAR
        }
    }

    @Register
    fun onSecondaryActionTimerTimeout() {
        if (mainStateMachine.getCurrentNode() == idleName) {
            shake()
        }
        secondaryActionTimer.start(GD.randfRange(3f, 8f).toDouble())
    }

    @Register
    fun idle() {
        mainStateMachine.travel(idleName)
    }

    @Register
    fun walk() {
        mainStateMachine.travel(walkName)
    }

    @Register
    fun shake() {
        mainStateMachine.travel(shakeName)
    }

    @Register
    fun attack() {
        mainStateMachine.travel(attackName)
    }

    @Register
    fun powerOff() {
        mainStateMachine.travel(powerOffName)
        secondaryActionTimer.stop()
    }
}
