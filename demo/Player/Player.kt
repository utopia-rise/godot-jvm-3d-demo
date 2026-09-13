package Player

import Player.Coin.Coin
import godot.annotation.Export
import godot.annotation.Script
import godot.annotation.Register
import godot.annotation.Visible
import godot.annotation.Emit
import godot.api.AnimationTree
import godot.api.AudioStreamPlayer3D
import godot.api.CharacterBody3D
import godot.api.ColorRect
import godot.api.Input
import godot.api.InputEventKey
import godot.api.InputEventMouseButton
import godot.api.InputMap
import godot.api.PackedScene
import godot.api.ShapeCast3D
import godot.core.Basis
import godot.core.Key
import godot.core.MouseButton
import godot.core.Vector3
import godot.core.asCachedStringName
import godot.core.asStringName
import godot.core.signal1
import godot.coroutines.await
import godot.coroutines.launch
import godot.extension.connectMethod
import icons.WeaponUI
import godot.global.GD
import shared.Damageable

enum class WeaponType {
    DEFAULT,
    GRENADE
}

@Script
class Player : CharacterBody3D(), Damageable {
    @Emit("weapon_name")
    val weaponSwitched by signal1<String>()

    @Export
    lateinit var bulletScene: PackedScene

    @Export
    lateinit var coinScene: PackedScene

    @Export
    var moveSpeed = 8.1

    @Export
    var bulletSpeed = 10.0

    @Export
    var attackImpulse = 10.0

    @Export
    var acceleration = 5.0

    @Export
    var jumpInitialImpulse = 12.0

    @Export
    var jumpAdditionalForce = 4.5

    @Export
    var rotationSpeed = 12.0

    @Export
    var stoppingSpeed = 1.0

    @Export
    var maxThrowbackForce = 15.0

    @Export
    var shootCooldown = 0.5

    @Export
    var grenadeCooldown = 0.5

    @Export
    lateinit var cameraController: CameraController

    @Export
    lateinit var groundShapecast: ShapeCast3D

    @Export
    lateinit var grenadeAimController: GrenadeLauncher

    @Export
    lateinit var characterSkin: CharacterSkin

    @Export
    lateinit var characterAnimationTree: AnimationTree

    @Export
    lateinit var characterMeleeArea: MeleeAttackArea

    @Export
    lateinit var weaponsUi: WeaponUI

    @Export
    lateinit var uiAimRecticle: ColorRect

    @Export
    lateinit var uiCoinsContainer: CoinsContainer

    @Export
    lateinit var stepSound: AudioStreamPlayer3D

    @Export
    lateinit var landingSound: AudioStreamPlayer3D

    @Visible
    var groundHeight = 0.0

    private var equipedWeapon = WeaponType.DEFAULT
    private var moveDirection = Vector3.ZERO
    private var lastStrongDirection = Vector3.FORWARD
    private val gravity = -30.0
    private lateinit var startPosition: Vector3
    private var coins = 0
    private var isOnFloorBuffer = false

    private var shootCooldownTick = shootCooldown
    private var grenadeCooldownTick = grenadeCooldown

    override fun _ready() {
        startPosition = globalPosition
        characterSkin.stepped.connectMethod(this, Player::playFootStepSound)
        weaponSwitched.connectMethod(weaponsUi, WeaponUI::switchTo)
        Input.setMouseMode(Input.MouseMode.CAPTURED)
        cameraController.setup(this)
        grenadeAimController.visible = false
        weaponSwitched.emit(WeaponType.DEFAULT.name)

        // When copying this character to a new project, the project may lack required input actions.
        // In that case, we register input actions for the user at runtime.
        if (!InputMap.hasAction("move_left".asStringName())) {
            registerInputActions()
        }
    }

    override fun _physicsProcess(delta: Double) {
        // Calculate ground height for camera controller
        if (groundShapecast.getCollisionCount() > 0) {
            (0 until groundShapecast.getCollisionCount()).forEach { collisionIndex ->
                val collisionPoint = groundShapecast.getCollisionPoint(collisionIndex)
                groundHeight = GD.max(groundHeight, collisionPoint.y)
            }
        } else {
            groundHeight = globalPosition.y + groundShapecast.targetPosition.y
        }

        if (globalPosition.y < groundHeight) {
            groundHeight = globalPosition.y
        }

        // Swap weapons
        if (Input.isActionJustPressed("swap_weapons".asCachedStringName())) {
            equipedWeapon = when (equipedWeapon) {
                WeaponType.DEFAULT -> WeaponType.GRENADE
                WeaponType.GRENADE -> WeaponType.DEFAULT
            }
            grenadeAimController.visible = equipedWeapon == WeaponType.GRENADE
            weaponSwitched.emit(equipedWeapon.name)
        }

        // Get input and movement state
        val isAttacking = Input.isActionPressed("attack".asStringName()) && !characterMeleeArea.isActive()
        val isJustAttacking = Input.isActionJustPressed("attack".asStringName())
        val isJustJumping = Input.isActionJustPressed("jump".asStringName()) && isOnFloor()
        val isAiming = Input.isActionPressed("aim".asStringName()) && isOnFloor()
        val isAirBoosting = Input.isActionPressed("jump".asStringName()) && !isOnFloor() && velocity.y > 0.0
        val isJustOnFloor = isOnFloor() && !isOnFloorBuffer

        isOnFloorBuffer = isOnFloor()
        moveDirection = getCameraOrientedInput()

        // To not orient quickly to the last input, we save a last strong direction,
        // this also ensures a good normalized value for the rotation basis.
        if (moveDirection.length() > 0.2) {
            lastStrongDirection = moveDirection.normalized()
        }

        if (isAiming) {
            lastStrongDirection = (cameraController.globalTransform.basis * Vector3.BACK).normalized()
        }

        orientCharacterToDirection(lastStrongDirection, delta)

        // On the ground the horizontal velocity always follows the input. In the air it only does so while
        // the player is actually steering, at half the acceleration, so a launch from a tilted jumping pad keeps
        // its momentum when no key is pressed.
        val hasMoveInput = moveDirection.length() > 0.0
        if (isOnFloor()) {
            // We separate out the y velocity to not interpolate on the gravity
            val yVelocity = velocity.y
            velocityMutate { y = 0.0 }
            velocity = velocity.lerp(moveDirection * moveSpeed, acceleration * delta)
            if (!hasMoveInput && velocity.length() < stoppingSpeed) {
                velocity = Vector3.ZERO
            }
            velocityMutate { y = yVelocity }
        } else if (hasMoveInput) {
            velocity = applyAirControl(velocity, moveDirection, delta)
        }

        // Set aiming camera and UI
        if (isAiming) {
            cameraController.setPivot(CameraController.CameraPivot.OVER_SHOULDER.ordinal)
            grenadeAimController.throwDirection = cameraController.camera.quaternion * Vector3.FORWARD
            grenadeAimController.fromLookPosition = cameraController.camera.globalPosition
            uiAimRecticle.visible = true
        } else {
            cameraController.setPivot(CameraController.CameraPivot.THIRD_PERSON.ordinal)
            grenadeAimController.throwDirection = lastStrongDirection
            grenadeAimController.fromLookPosition = globalPosition
            uiAimRecticle.visible = false
        }

        // Update attack state and position
        shootCooldownTick += delta
        grenadeCooldownTick += delta

        if (isAttacking) {
            when (equipedWeapon) {
                WeaponType.DEFAULT -> if (isAiming && isOnFloor()) {
                    if (shootCooldownTick > shootCooldown) {
                        shootCooldownTick = 0.0
                        shoot()
                    }
                } else if (isJustAttacking) {
                    attack()
                }

                WeaponType.GRENADE -> if (grenadeCooldownTick > grenadeCooldown) {
                    grenadeCooldownTick = 0.0
                    grenadeAimController.throwGrenade()
                }
            }
        }

        velocityMutate { y += gravity * delta }

        if (isJustJumping) {
            velocityMutate { y += jumpInitialImpulse }
        } else if (isAirBoosting) {
            velocityMutate { y += jumpAdditionalForce * delta }
        }

        // Set character animation
        when {
            isJustJumping -> characterSkin.jump()
            !isOnFloor() && velocity.y < 0 -> characterSkin.fall()
            isOnFloor() -> {
                val xzVelocity = Vector3(velocity.x, 0, velocity.z)
                if (xzVelocity.length() > stoppingSpeed) {
                    characterSkin.walkRunBlending = xzVelocity.length() / moveSpeed
                    characterSkin.move()
                } else {
                    characterSkin.idle()
                }
            }
        }

        if (isJustOnFloor) {
            landingSound.play()
        }

        // Safety net in case the character somehow misses the death plane.
        if (globalPosition.y < -40.0) {
            resetPosition()
            velocity = Vector3.ZERO
        }

        val positionBefore = globalPosition
        moveAndSlide()
        val positionAfter = globalPosition

        // If velocity is not 0 but the difference of positions after move_and_slide is,
        // character might be stuck somewhere!
        val deltaPosition = positionAfter - positionBefore
        val epsilon = 0.001
        if (deltaPosition.length() < epsilon && velocity.length() > epsilon) {
            globalPosition += getWallNormal() * 0.1
        }
    }

    /**
     * Steers the horizontal velocity toward the input while airborne, at half the ground acceleration.
     * Momentum along the input direction is conserved when already faster than the run speed, so steering
     * into a jumping pad launch never slows the character down. Steering against it still brakes.
     */
    private fun applyAirControl(currentVelocity: Vector3, input: Vector3, delta: Double): Vector3 {
        val weight = acceleration * 0.5 * delta
        val inputDirection = input.normalized()
        val horizontal = Vector3(currentVelocity.x, 0.0, currentVelocity.z)

        val alongInput = horizontal.dot(inputDirection)
        val targetAlongInput = input.length() * moveSpeed
        val newAlongInput = if (alongInput >= targetAlongInput) {
            alongInput
        } else {
            GD.lerp(alongInput, targetAlongInput, weight)
        }

        // Sideways drift is steered toward the input direction.
        val sideways = horizontal - inputDirection * alongInput
        val newSideways = sideways.lerp(Vector3.ZERO, weight)

        val result = inputDirection * newAlongInput + newSideways
        return Vector3(result.x, currentVelocity.y, result.z)
    }

    private fun attack() {
        velocity = characterSkin.transform.basis * Vector3.BACK * attackImpulse

        characterSkin.attack()
        characterMeleeArea.activate()
        launch {
            characterAnimationTree.animationFinished.await()
            characterMeleeArea.deactivate()
        }
    }

    private fun shoot() {
        val bullet = bulletScene.instantiate() as Bullet
        bullet.shooter = this
        val origin = globalPosition + Vector3.UP
        val aimTarget = cameraController.getAimTarget()
        val aimDirection = (aimTarget - origin).normalized()
        bullet.velocity = aimDirection * bulletSpeed
        bullet.distanceLimit = 14f
        getParent()?.addChild(bullet)
        bullet.globalPosition = origin
    }

    @Register
    fun resetPosition() = transformMutate {
        origin = startPosition
    }

    @Register
    fun collectCoin() {
        coins += 1
        uiCoinsContainer.updateCoinsAmount(coins)
    }

    @Register
    fun looseCoins() {
        val lostCoins = GD.min(coins, 5)
        coins -= lostCoins
        repeat(lostCoins) {
            val coin = coinScene.instantiate() as Coin
            getParent()?.addChild(coin)
            coin.globalPosition = globalPosition
            coin.spawn()
        }
        uiCoinsContainer.updateCoinsAmount(coins)
    }

    private fun getCameraOrientedInput(): Vector3 {
        if (characterMeleeArea.isActive()) return Vector3.ZERO

        val rawInput = Input.getVector(
            "move_left".asStringName(),
            "move_right".asStringName(),
            "move_up".asStringName(),
            "move_down".asStringName()
        )

        var input = Vector3.ZERO
        // This is to ensure that diagonal input isn't stronger than axis aligned input
        input.x = -rawInput.x * GD.sqrt(1.0 - rawInput.y * rawInput.y / 2.0)
        input.z = -rawInput.y * GD.sqrt(1.0 - rawInput.x * rawInput.x / 2.0)

        input = cameraController.globalTransform.basis * input
        input.y = 0.0
        return input
    }

    @Register
    fun playFootStepSound() {
        stepSound.pitchScale = GD.randfn(1.2f, 0.2f)
        stepSound.play()
    }

    @Register
    override fun damage(impactPoint: Vector3, velocity: Vector3) {
        // Always throws character up
        velocity.y = GD.abs(velocity.y)
        this@Player.velocity = velocity.limitLength(maxThrowbackForce)
        looseCoins()
    }

    private fun orientCharacterToDirection(direction: Vector3, delta: Double) {
        val leftAxis = Vector3.UP.cross(direction)
        val rotationBasis = Basis(leftAxis, Vector3.UP, direction).getRotationQuaternion()
        val modelScale = characterSkin.transform.basis.getScale()

        val newBasis = Basis(
            characterSkin
                .transform
                .basis
                .getRotationQuaternion()
                .slerp(rotationBasis, delta * rotationSpeed)
        ).scaled(modelScale)

        characterSkin.transformMutate { basis = newBasis }
    }

    // Used to register required input actions when copying this character to a different project.
    private fun registerInputActions() {
        val inputKeyActions = mapOf(
            "move_left".asStringName() to Key.A,
            "move_right".asStringName() to Key.D,
            "move_up".asStringName() to Key.W,
            "move_down".asStringName() to Key.S,
            "jump".asStringName() to Key.SPACE,
            "swap_weapons".asStringName() to Key.TAB,
            "pause".asStringName() to Key.ESCAPE,
            "camera_left".asStringName() to Key.Q,
            "camera_right".asStringName() to Key.E,
            "camera_up".asStringName() to Key.R,
            "camera_down".asStringName() to Key.F,
        )

        val inputMouseActions = mapOf(
            "attack".asStringName() to MouseButton.LEFT,
            "aim".asStringName() to MouseButton.RIGHT,
        )

        inputKeyActions.forEach { (action, key) ->
            if (!InputMap.hasAction(action)) {
                InputMap.addAction(action)
                val inputKey = InputEventKey().apply { keycode = key }
                InputMap.actionAddEvent(action, inputKey)
            }
        }

        inputMouseActions.forEach { (action, button) ->
            if (!InputMap.hasAction(action)) {
                InputMap.addAction(action)
                val inputKey = InputEventMouseButton().apply { buttonIndex = button }
                InputMap.actionAddEvent(action, inputKey)
            }
        }
    }
}
