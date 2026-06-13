package me.jfenn.bingo.platform.event.model

import me.jfenn.bingo.platform.event.IEvent
import net.minecraft.server.MinecraftServer

data class ServerEvent(
    val server: MinecraftServer,
) {
    object Started: IEvent<ServerEvent>
    object Stopped: IEvent<ServerEvent>
    object Saved: IEvent<ServerEvent>
}