package me.jfenn.bingo.mixinhandler

import net.minecraft.server.network.ServerPlayerEntity
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

object ServerPlayNetworkHandlerMixinHandler {

    private val playerMap = ConcurrentHashMap<Int, Instant>()

    fun getLastPlayerMovement(player: ServerPlayerEntity): Instant? {
        val id = System.identityHashCode(player)
        return playerMap[id]
    }

    fun onPlayerMove(player: ServerPlayerEntity) {
        val id = System.identityHashCode(player)
        playerMap[id] = Instant.now()
    }
}