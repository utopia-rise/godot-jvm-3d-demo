package Player.model.face

import godot.annotation.Export
import godot.annotation.Register
import godot.annotation.Script
import godot.api.AnimationPlayer
import godot.api.Node2D
import godot.api.Sprite2D
import godot.api.Texture2D
import godot.api.Timer
import godot.core.asStringName
import godot.coroutines.await
import godot.coroutines.launch
import godot.global.GD

/**
 * 2D face of GDBot, rendered into a SubViewport and displayed on the character's screen by
 * the screen shader. Handles blinking and named expressions ("default", "happy", "dizzy", "sleepy").
 */
@Script
class GDBotFace : Node2D() {

    @Export
    lateinit var animationPlayer: AnimationPlayer

    @Export
    lateinit var blinkingTimer: Timer

    @Export
    lateinit var closedEyesTimer: Timer

    @Export
    lateinit var leftEye: Sprite2D

    @Export
    lateinit var rightEye: Sprite2D

    @Export
    lateinit var eyeOpenTexture: Texture2D

    @Export
    lateinit var eyeClosedTexture: Texture2D

    private val resetAnimation = "RESET".asStringName()
    private val lookAroundAnimation = "look_around".asStringName()

    private var blinking = false
        set(value) {
            field = value
            if (field) blinkingTimer.start() else blinkingTimer.stop()
        }

    var currentFace: String = ""
        private set

    override fun _ready() {
        launch {
            while (true) {
                blinkingTimer.timeout.await()
                onBlinkTimerTimeout()
            }
        }
        setFace("default")
    }

    private suspend fun onBlinkTimerTimeout() {
        if (GD.randfRange(0.0f, 1.0f) > 0.9f) {
            // Play a secondary action rather than a blink.
            animationPlayer.play(lookAroundAnimation)
            animationPlayer.animationFinished.await()
        } else {
            setEyes(eyeClosedTexture)
            closedEyesTimer.start(GD.randfRange(0.1f, 0.25f).toDouble())
            closedEyesTimer.timeout.await()
        }
        // The face may have changed while waiting.
        if (!blinking) return
        setEyes(eyeOpenTexture)
        blinkingTimer.waitTime = if (GD.randfRange(0.0f, 1.0f) > 0.8f) {
            GD.randfRange(0.1f, 0.15f).toDouble()
        } else {
            GD.randfRange(1.0f, 4.0f).toDouble()
        }
        blinkingTimer.start()
    }

    private fun setEyes(texture: Texture2D) {
        leftEye.texture = texture
        rightEye.texture = texture
    }

    @Register
    fun setFace(faceName: String) {
        if (currentFace == faceName) return
        currentFace = faceName
        animationPlayer.play(resetAnimation)
        animationPlayer.seek(0.0, true)
        if (faceName == "default") {
            blinking = true
            return
        }
        blinking = false
        val animationName = faceName.asStringName()
        if (!animationPlayer.hasAnimation(animationName)) {
            GD.pushError("Can't set GDBot's face to: '$faceName'")
            return
        }
        animationPlayer.play(animationName)
    }
}
