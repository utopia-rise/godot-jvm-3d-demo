package Player

import godot.annotation.Register
import godot.annotation.Script
import godot.api.Area3D
import godot.api.Node3D
import godot.extension.connectMethod
import shared.Damageable

@Script
class MeleeAttackArea : Area3D() {

    override fun _ready() {
        bodyEntered.connectMethod(this, MeleeAttackArea::onBodyEntered)
    }

    @Register
    fun isActive(): Boolean = monitoring

    @Register
    fun activate() {
        monitoring = true
    }

    @Register
    fun deactivate() {
        monitoring = false
    }

    @Register
    fun onBodyEntered(body: Node3D) {
        if (body is Damageable) {
            val impactPoint = globalPosition - body.globalPosition
            val force = -impactPoint

            body.damage(impactPoint, force)
        }
    }
}
