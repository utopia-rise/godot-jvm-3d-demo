package Player

import Player.model.face.GDBotFace
import godot.annotation.DoubleRange
import godot.annotation.Emit
import godot.annotation.Export
import godot.annotation.Register
import godot.annotation.Script
import godot.api.AnimationNodeOneShot
import godot.api.AnimationNodeStateMachinePlayback
import godot.api.AnimationTree
import godot.api.Node3D
import godot.core.Signal0
import godot.core.asStringName
import godot.core.signal0
import godot.global.GD

@Script
class CharacterSkin : Node3D() {

    /** Emitted when GDBot's feet hit the ground while running. Called by the "step" animation method track. */
    @Emit
    val stepped: Signal0 by signal0()

    @Export
    lateinit var animationTree: AnimationTree

    @Export
    lateinit var face: GDBotFace

    /**
     * Blending between the walking and running animations.
     * 0.0 is a full walk cycle, 1.0 a full run cycle.
     */
    @Export
    @DoubleRange(0.0, 1.0, 0.01)
    var walkRunBlending: Double = 0.0
        set(value) {
            field = GD.clamp(value, 0.0, 1.0)
            if (!isNodeReady()) return
            animationTree.set(walkRunBlendAmountPath, field)
            animationTree.set(stepTimeScalePath, GD.lerp(1.0, walkRunRatio, field))
        }

    private val mainStateMachine: AnimationNodeStateMachinePlayback by lazy {
        animationTree.get("parameters/StateMachine/playback".asStringName()) as AnimationNodeStateMachinePlayback
    }

    private val walkRunBlendAmountPath = "parameters/StateMachine/Move/WalkRunBlending/blend_amount".asStringName()
    private val stepTimeScalePath = "parameters/StateMachine/Move/StepTimeScale/scale".asStringName()
    private val attackOneShotPath = "parameters/AttackOneShot/request".asStringName()

    private val idleState = "Idle".asStringName()
    private val moveState = "Move".asStringName()
    private val jumpState = "Jump".asStringName()
    private val fallState = "Fall".asStringName()

    // The step animation is timed for the walk cycle, so it is sped up when blending toward the run cycle.
    private var walkRunRatio = 1.0

    override fun _ready() {
        val walkLength = animationTree.getAnimation("walk".asStringName())!!.length
        val runLength = animationTree.getAnimation("run".asStringName())!!.length
        walkRunRatio = walkLength / runLength
        animationTree.active = true
        // Re-apply so the tree parameters match the exported value.
        walkRunBlending = walkRunBlending
    }

    /** Sets the model to a neutral, action-free state. */
    @Register
    fun idle() {
        mainStateMachine.travel(idleState)
    }

    /** Sets the model to the walk/run blend, see [walkRunBlending]. */
    @Register
    fun move() {
        mainStateMachine.travel(moveState)
    }

    @Register
    fun jump() {
        mainStateMachine.travel(jumpState)
    }

    @Register
    fun fall() {
        mainStateMachine.travel(fallState)
    }

    @Register
    fun attack() {
        animationTree.set(attackOneShotPath, AnimationNodeOneShot.OneShotRequest.FIRE.value)
    }

    /**
     * Changes the facial expression. Possible values are "default" (blinking), "happy", "dizzy" and "sleepy".
     * New expressions can be added as animations in GDBotFace.tscn.
     */
    @Register
    fun setFace(faceName: String) {
        face.setFace(faceName)
    }
}
