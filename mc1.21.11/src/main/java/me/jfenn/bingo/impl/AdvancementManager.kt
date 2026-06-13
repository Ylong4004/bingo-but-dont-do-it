package me.jfenn.bingo.impl

import me.jfenn.bingo.platform.text.IText
import me.jfenn.bingo.platform.IAdvancementHandle
import me.jfenn.bingo.platform.IAdvancementManager
import me.jfenn.bingo.platform.item.IItemStack
import me.jfenn.bingo.platform.item.IItemStackFactory
import net.minecraft.advancement.AdvancementEntry
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import kotlin.jvm.optionals.getOrNull

class AdvancementManager(
    private val itemStackFactory: IItemStackFactory,
) : IAdvancementManager {
    override fun listAdvancements(server: MinecraftServer): List<String> {
        return server.advancementLoader.advancements
            .filter { !it.id.path.endsWith("/root") && it.value.display.isPresent }
            .map { it.id.toString() }
    }

    override fun getAdvancement(server: MinecraftServer, id: String): IAdvancementHandle? {
        return server.advancementLoader.get(Identifier.of(id))
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
        player.entityWorld.server?.advancementLoader?.advancements?.forEach { advancement ->
            val progress = player.advancementTracker.getProgress(advancement)
            for (criteria in progress.obtainedCriteria) {
                player.advancementTracker.revokeCriterion(advancement, criteria)
            }
        }
    }
}

class AdvancementHandle(
    val advancement: AdvancementEntry,
    private val itemStackFactory: IItemStackFactory,
) : IAdvancementHandle {
    override val name: IText?
        get() = advancement.value.display.getOrNull()?.title
            ?.let { TextImpl(it.copy()) }
    override val description: IText?
        get() = advancement.value.display.getOrNull()?.description
            ?.let { TextImpl(it.copy()) }
    override val displayIcon: IItemStack?
        get() = advancement.value.display.getOrNull()?.icon
            ?.let { itemStackFactory.forStack(it) }
}
