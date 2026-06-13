package me.jfenn.bingo.platform.event.model

import me.jfenn.bingo.platform.event.IEvent
import net.minecraft.entity.Entity
import net.minecraft.server.MinecraftServer
import net.minecraft.server.world.ServerWorld

data class EntityLoadEvent(
    val server: MinecraftServer,
    val entity: Entity,
    val world: ServerWorld
) {
    companion object : IEvent<EntityLoadEvent>
}