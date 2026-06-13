package me.jfenn.bingo.common.commands

import me.jfenn.bingo.common.event.ScopedEvents
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity

class CommandTreeHandler(
    private val server: MinecraftServer,
    events: ScopedEvents,
) {

    private fun sendCommandTree(playerEntity: ServerPlayerEntity) {
        server.playerManager.sendCommandTree(playerEntity)
    }

    init {
        events.onStateChange {
            for (player in server.playerManager.playerList.toList()) {
                sendCommandTree(player)
            }
        }

        events.onChangeTeam {
            sendCommandTree(it.player.player)
        }
    }

}