package me.jfenn.bingo.platform

import me.jfenn.bingo.platform.text.IText
import me.jfenn.bingo.platform.item.IItemStack
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity

interface IAdvancementManager {

    fun listAdvancements(server: MinecraftServer): List<String>

    fun getAdvancement(server: MinecraftServer, id: String): IAdvancementHandle?

    fun getProgress(player: ServerPlayerEntity, advancement: IAdvancementHandle): Float

    fun isAnyObtained(player: ServerPlayerEntity, advancement: IAdvancementHandle): Boolean

    fun isDone(player: ServerPlayerEntity, advancement: IAdvancementHandle): Boolean

    fun clearAdvancements(player: ServerPlayerEntity)

}

interface IAdvancementHandle {
    val name: IText?
    val description: IText?
    val displayIcon: IItemStack?
}
