package me.jfenn.bingo.impl

import me.jfenn.bingo.platform.text.IText
import me.jfenn.bingo.platform.IAdvancementHandle
import me.jfenn.bingo.platform.IAdvancementManager
import me.jfenn.bingo.platform.item.IItemStack
import me.jfenn.bingo.platform.item.IItemStackFactory
import net.minecraft.advancement.Advancement
import net.minecraft.server.MinecraftServer
import net.minecraft.server.command.CommandOutput
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier

class AdvancementManager(
    private val itemStackFactory: IItemStackFactory,
) : IAdvancementManager {
    override fun listAdvancements(server: MinecraftServer): List<String> {
        return server.advancementLoader.advancements
            .filter { !it.id.path.endsWith("/root") && it.display != null }
            .map { it.id.toString() }
    }

    override fun getAdvancement(server: MinecraftServer, id: String): IAdvancementHandle? {
        return server.advancementLoader.get(Identifier(id))
            ?.let { AdvancementHandle(it, itemStackFactory) }
    }

    override fun getProgress(player: ServerPlayerEntity, advancement: IAdvancementHandle): Float {
        require(advancement is AdvancementHandle)
        return player.advancementTracker.getProgress(advancement.advancement).progressBarPercentage
    }

    override fun isAnyObtained(player: ServerPlayerEntity, advancement: IAdvancementHandle): Boolean {
        require(advancement is AdvancementHandle)
        return player.advancementTracker.getProgress(advancement.advancement).isAnyObtained
    }

    override fun isDone(player: ServerPlayerEntity, advancement: IAdvancementHandle): Boolean {
        require(advancement is AdvancementHandle)
        return player.advancementTracker.getProgress(advancement.advancement).isDone
    }

    override fun clearAdvancements(player: ServerPlayerEntity) {
        // The yarn mappings for these APIs conflict between versions, so we're using the command for this instead
        player.server.commandManager.executeWithPrefix(player.server.commandSource.withOutput(CommandOutput.DUMMY), "advancement revoke ${player.entityName} everything")
    }
}

class AdvancementHandle(
    val advancement: Advancement,
    private val itemStackFactory: IItemStackFactory,
) : IAdvancementHandle {
    override val name: IText?
        get() = advancement.display?.title
            ?.let { TextImpl(it.copy()) }
    override val description: IText?
        get() = advancement.display?.description
            ?.let { TextImpl(it.copy()) }
    override val displayIcon: IItemStack?
        get() = advancement.display?.icon
            ?.let { itemStackFactory.forStack(it) }
}