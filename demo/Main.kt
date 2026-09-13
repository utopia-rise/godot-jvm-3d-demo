import godot.annotation.Export
import godot.annotation.Script
import godot.api.Node
import godot.api.Node3D
import godot.api.RenderingServer
import godot.coroutines.await
import godot.coroutines.launch

@Script
class Main : Node3D() {

    /**
     * Hidden node holding instances of the effects (grenade, explosion, coin, smoke puff) so their shaders
     * are compiled during the first frame instead of stuttering the first time they appear in-game.
     */
    @Export
    lateinit var fixStutters: Node

    override fun _ready() {
        launch {
            RenderingServer.framePostDraw.await()
            fixStutters.queueFree()
        }
    }
}
