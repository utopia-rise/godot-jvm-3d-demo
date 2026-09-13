package Player

import godot.annotation.DoubleRange
import godot.annotation.Export
import godot.annotation.Script
import godot.annotation.Register
import godot.annotation.Visible
import godot.api.Camera3D
import godot.api.Input
import godot.api.InputEvent
import godot.api.InputEventMouseMotion
import godot.api.Node
import godot.api.Node3D
import godot.api.RayCast3D
import godot.api.SpringArm3D
import godot.core.Basis
import godot.core.Vector3
import godot.core.asStringName
import godot.global.GD

@Script
class CameraController : Node3D() {
    enum class CameraPivot {
        OVER_SHOULDER, THIRD_PERSON
    }

    @Export
    var invertMouseY = false

    @Export
    @DoubleRange(0.0, 1.0)
    var mouseSensitivity = 0.25

    @Export
    @DoubleRange(0.0, 8.0)
    var joystickSensitivity = 2.0

    @Export
    var tiltUpperLimit = GD.degToRad(-60.0)

    @Export
    var tiltLowerLimit = GD.degToRad(60.0)

    @Export
    lateinit var camera: Camera3D

    @Export
    lateinit var overShoulderPivot: Node3D

    @Export
    lateinit var cameraSpringArm: SpringArm3D

    @Export
    lateinit var thirdPersonPivot: Node3D

    @Export
    lateinit var cameraRayCast: RayCast3D

    private var aimTarget: Vector3 = Vector3()
    private var aimCollider: Node? = null
    private lateinit var pivot: Node3D
    private var currentPivotType = CameraPivot.OVER_SHOULDER
    private var rotationInput: Double = 0.0
    private var tiltInput: Double = 0.0
    private var offset: Vector3 = Vector3.ZERO
    private lateinit var anchor: Player
    private val eulerRotation: Vector3 = Vector3.ZERO

    private val cameraLeftAction = "camera_left".asStringName()
    private val cameraRightAction = "camera_right".asStringName()
    private val cameraUpAction = "camera_up".asStringName()
    private val cameraDownAction = "camera_down".asStringName()

    override fun _unhandledInput(event: InputEvent) {
        if (event is InputEventMouseMotion && Input.getMouseMode() == Input.MouseMode.CAPTURED) {
            rotationInput = -event.relative.x * mouseSensitivity
            tiltInput = -event.relative.y * mouseSensitivity
        }
    }

    override fun _process(delta: Double) {
        if (!::anchor.isInitialized) return
        if (!::pivot.isInitialized) return

        rotationInput += Input.getActionRawStrength(cameraLeftAction) - Input.getActionRawStrength(cameraRightAction)
        tiltInput += Input.getActionRawStrength(cameraUpAction) - Input.getActionRawStrength(cameraDownAction)

        if (invertMouseY) {
            tiltInput *= -1
        }

        if (cameraRayCast.isColliding()) {
            aimTarget = cameraRayCast.getCollisionPoint()
            aimCollider = cameraRayCast.getCollider() as? Node3D
        } else {
            aimTarget = cameraRayCast.globalPosition * cameraRayCast.targetPosition
            aimCollider = null
        }

        // Set camera controller to current ground level for the character
        val targetPosition = anchor.globalPosition + offset
        targetPosition.y = GD.lerp(globalPosition.y, anchor.groundHeight, 0.1)
        globalPosition = targetPosition

        // Rotates camera using euler rotation
        eulerRotation.x += tiltInput * delta
        eulerRotation.x = GD.clamp(eulerRotation.x, tiltLowerLimit, tiltUpperLimit)
        eulerRotation.y += rotationInput * delta

        transformMutate {
            basis = Basis.fromEuler(eulerRotation)
        }

        camera.globalTransform = pivot.globalTransform
        camera.rotationMutate {
            z = 0.0
        }

        rotationInput = 0.0
        tiltInput = 0.0
    }

    @Register
    fun setup(anchor: Player) {
        this.anchor = anchor
        globalTransform = anchor.globalTransform
        this.offset = this.globalTransform.origin - anchor.globalTransform.origin
        setPivot(CameraPivot.THIRD_PERSON.ordinal)
        camera.globalTransform = camera.globalTransform.interpolateWith(pivot.globalTransform, 0.1)
        cameraSpringArm.addExcludedObject(anchor.getRid())
        cameraRayCast.addExceptionRid(anchor.getRid())
    }

    @Register
    fun setPivot(pivotOrdinal: Int) {
        val pivotType = CameraPivot.entries[pivotOrdinal]
        if (pivotType == currentPivotType) return

        pivot = when (pivotType) {
            CameraPivot.OVER_SHOULDER -> {
                overShoulderPivot.lookAt(aimTarget)
                overShoulderPivot
            }

            CameraPivot.THIRD_PERSON -> thirdPersonPivot
        }
        currentPivotType = pivotType
    }

    @Register
    fun getAimTarget(): Vector3 {
        return aimTarget
    }

    @Register
    fun getAimCollider(): Node? {
        return aimCollider?.takeIf { GD.isInstanceValid(it) }
    }
}
