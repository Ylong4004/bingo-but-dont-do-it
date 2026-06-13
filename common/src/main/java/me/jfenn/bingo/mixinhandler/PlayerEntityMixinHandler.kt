package me.jfenn.bingo.mixinhandler

import me.jfenn.bingo.platform.text.IText
import me.jfenn.bingo.common.NBT_BINGO_KEEP
import me.jfenn.bingo.common.NBT_BINGO_VANISH
import me.jfenn.bingo.common.ready.ReadyTimerState
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.platform.event.IEventBus
import me.jfenn.bingo.platform.event.game.ScopeStopped
import me.jfenn.bingo.platform.item.IItemStackFactory
import net.minecraft.item.ItemStack
import net.minecraft.util.Formatting
import java.util.*

class PlayerEntityMixinHandler(
    private val itemStackFactory: IItemStackFactory,
    private val readyTimerState: ReadyTimerState,
    private val text: TextProvider,
    eventBus: IEventBus,
) {

    fun shouldVanishDrop(stack: ItemStack): Boolean {
        val itemStack = itemStackFactory.forStack(stack)
        return itemStack.hasCustomTag(NBT_BINGO_VANISH)
    }

    fun shouldKeepDrop(stack: ItemStack): Boolean {
        val itemStack = itemStackFactory.forStack(stack)
        return itemStack.hasCustomTag(NBT_BINGO_KEEP)
    }

    fun getPlayerListName(uuid: UUID, name: IText): IText? {
        return if (readyTimerState.isRunning && readyTimerState.isReady(uuid)) {
            text.empty()
                .append(text.literal("✔ ").formatted(Formatting.GREEN))
                .append(name)
        } else {
            null
        }
    }

    init {
        instance = this
        eventBus.register(ScopeStopped) {
            instance = null
        }
    }

    companion object {
        var instance: PlayerEntityMixinHandler? = null

        fun shouldVanishDrop(stack: ItemStack) = instance?.shouldVanishDrop(stack) ?: false

        fun shouldKeepDrop(stack: ItemStack) = instance?.shouldKeepDrop(stack) ?: false

        fun getPlayerListName(uuid: UUID, name: IText) = instance?.getPlayerListName(uuid, name)
    }
}