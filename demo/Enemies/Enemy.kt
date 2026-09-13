package Enemies

import Enemies.smoke_puff.SmokePuff
import Player.Coin.Coin
import godot.api.PackedScene
import godot.api.ResourceLoader
import godot.api.RigidBody3D
import godot.core.Vector3
import godot.coroutines.await
import godot.coroutines.launch
import godot.extension.instantiateAs
import godot.extension.loadAs
import shared.Damageable

abstract class Enemy : RigidBody3D(), Damageable {
    abstract var coinsCount: Int

    abstract val spawnHeight: Double

    override fun _ready() {
        if (spawnHeight != 0.0) {
            globalPosition += Vector3.UP * spawnHeight
        }
    }

    private val puffScene = ResourceLoader.loadAs<PackedScene>("res://demo/Enemies/smoke_puff/smoke_puff.tscn")!!
    private val coinScene = ResourceLoader.loadAs<PackedScene>("res://demo/Player/Coin/Coin.tscn")!!

    fun death() {
        val timer = getTree()!!.createTimer(2.0)
        launch {
            timer.timeout.await()

            val puff = puffScene.instantiateAs<SmokePuff>()!!
            getParent()?.addChild(puff)
            puff.globalPosition = globalPosition

            val coins = List(coinsCount) {
                coinScene.instantiateAs<Coin>()!!
            }

            for (coin in coins) {
                getParent()?.addChild(coin)
                coin.globalPosition = globalPosition
                coin.spawn()
            }

            queueFree()
        }
    }
}
