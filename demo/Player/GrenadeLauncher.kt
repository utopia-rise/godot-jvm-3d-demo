package Player

import godot.annotation.Export
import godot.annotation.Script
import godot.annotation.Register
import godot.annotation.Visible
import godot.api.CharacterBody3D
import godot.api.Engine
import godot.api.Marker3D
import godot.api.Mesh
import godot.api.MeshInstance3D
import godot.api.Node3D
import godot.api.PackedScene
import godot.api.PhysicsServer3D
import godot.api.ProjectSettings
import godot.api.ShapeCast3D
import godot.api.SurfaceTool
import godot.core.Vector2
import godot.core.Vector3
import godot.core.asStringName
import godot.global.GD

@Script
class GrenadeLauncher : Node3D() {
    @Export
    lateinit var grenadeScene: PackedScene

    @Export
    var minThrowDistance: Double = 7.0

    @Export
    var maxThrowDistance: Double = 7.0

    @Export
    var gravity: Double = ProjectSettings.getSetting("physics/3d/default_gravity") as Double

    @Export
    lateinit var snapMesh: Node3D

    @Export
    lateinit var raycast: ShapeCast3D

    @Export
    lateinit var launchPoint: Marker3D

    @Export
    lateinit var trailMeshInstance: MeshInstance3D

    @Visible
    var fromLookPosition = Vector3.ZERO

    @Visible
    var throwDirection = Vector3.ZERO

    private var throwVelocity = Vector3.ZERO
    private var timeToLand = 0.0

    override fun _ready() {
        if (Engine.isEditorHint()) {
            setPhysicsProcess(false)
        }
    }

    override fun _physicsProcess(delta: Double) {
        if (visible) {
            updateThrowVelocity()
            drawThrowPath()
        }
    }

    @Register
    fun throwGrenade(): Boolean {
        if (!visible) return false

        val grenade = grenadeScene.instantiate() as Grenade
        getParent()?.addChild(grenade)

        grenade.globalPosition = launchPoint.globalPosition
        grenade.`throw`(throwVelocity)
        val parentRID = (getParent() as CharacterBody3D).getRid()
        PhysicsServer3D.bodyAddCollisionException(parentRID, grenade.getRid())
        return true
    }

    private fun updateThrowVelocity() {
        val camera = getViewport()?.getCamera3d() ?: return
        val upRation = GD.clamp(GD.max(camera.rotation.x + 0.5, -0.4) * 2, 0.0, 1.0)

        val throwDistance = GD.lerp(minThrowDistance, maxThrowDistance, upRation)
        val globalCameraLookPosition = fromLookPosition + throwDirection * throwDistance

        raycast.targetPosition = globalCameraLookPosition - raycast.globalPosition

        // Snap grenade land position to an enemy the player's aiming at, if applicable
        var toTarget = raycast.targetPosition

        if (raycast.getCollisionCount() != 0) {
            val collider = raycast.getCollider(0) as? Node3D
            val hasTarget = collider != null && collider.isInGroup("targeteables".asStringName())
            snapMesh.visible = hasTarget

            if (hasTarget) {
                toTarget = collider.globalPosition - launchPoint.globalPosition
                snapMesh.globalPosition = launchPoint.globalPosition + toTarget
                snapMesh.lookAt(launchPoint.globalPosition)
            }
        } else {
            snapMesh.visible = false
        }

        // Calculate the initial velocity the grenade needs based on where we want it to land and how
        // high the curve should go.
        val peakHeight = GD.max(toTarget.y + 0.25, launchPoint.position.y + 0.25)

        val timeGoingUp = GD.sqrt(2.0 * peakHeight / gravity)

        val motionDown = toTarget.y - peakHeight
        val timeGoingDown = GD.sqrt(-2.0 * motionDown / gravity)

        timeToLand = timeGoingUp + timeGoingDown

        val targetPositionXZPlane = Vector3(toTarget.x, 0.0, toTarget.z)
        val startPositionXZPlane = Vector3(launchPoint.position.x, 0.0, launchPoint.position.z)

        val forwardVelocity = (targetPositionXZPlane - startPositionXZPlane) / timeToLand
        val velocityUp = GD.sqrt(2.0 * gravity * peakHeight)

        // Caching the found initial_velocity vector so we can use it on the throw() function
        throwVelocity = Vector3.UP * velocityUp + forwardVelocity
    }

    private fun drawThrowPath() {
        val timeStep = 0.05
        val trailWidth = 0.25

        val forwardDirection = Vector3(throwVelocity.x, 0.0, throwVelocity.z).normalized()
        val leftDirection = Vector3.UP.cross(forwardDirection)
        val offsetLeft = leftDirection * trailWidth / 2.0
        val offsetRight = -leftDirection * trailWidth / 2.0

        val surfaceTool = SurfaceTool()
        surfaceTool.begin(Mesh.PrimitiveType.TRIANGLES)

        val endTime = timeToLand + 0.5
        var pointPrevious = Vector3.ZERO
        var timeCurrent = 0.0
        // We'll create 2 triangles on each iteration, representing the quad of one
        // section of the path
        while (timeCurrent < endTime) {
            timeCurrent += timeStep
            val pointCurrent = throwVelocity * timeCurrent + Vector3.DOWN * gravity * 0.5 * timeCurrent * timeCurrent

            // Our point coordinates are at the center of the path, so we need to calculate vertices
            val trailPointLeftEnd = pointCurrent + offsetLeft
            val trailPointRightEnd = pointCurrent + offsetRight
            val trailPointLeftStart = pointPrevious + offsetLeft
            val trailPointRightStart = pointPrevious + offsetRight

            // UV position goes from 0 to 1, so we normalize the current iteration
            // to get the progress in the UV texture
            val uvProgressEnd = timeCurrent / endTime
            val uvProgressStart = uvProgressEnd - (timeStep / endTime)

            // Left side on the UV texture is at the top of the texture
            // (Vector2(0,1), or Vector2.DOWN). Right side on the UV texture is at
            // the bottom.
            val uvValueRightStart = (Vector2.RIGHT * uvProgressStart)
            val uvValueRightEnd = (Vector2.RIGHT * uvProgressEnd)
            val uvValueLeftStart = Vector2.DOWN + uvProgressStart
            val uvValueLeftEnd = Vector2.DOWN + uvProgressEnd

            pointPrevious = pointCurrent

            // Both triangles need to be drawn in the same orientation (Godot uses
            // clockwise orientation to determine the face normal)

            surfaceTool.apply {
                // Draw first triangle
                setUv(uvValueRightEnd)
                addVertex(trailPointRightEnd)
                setUv(uvValueLeftStart)
                addVertex(trailPointLeftStart)
                setUv(uvValueLeftEnd)
                addVertex(trailPointLeftEnd)

                // Draw second triangle
                setUv(uvValueRightStart)
                addVertex(trailPointRightStart)
                setUv(uvValueLeftStart)
                addVertex(trailPointLeftStart)
                setUv(uvValueRightEnd)
                addVertex(trailPointRightEnd)

                generateNormals()
            }
            trailMeshInstance.mesh = surfaceTool.commit()
        }
    }
}
