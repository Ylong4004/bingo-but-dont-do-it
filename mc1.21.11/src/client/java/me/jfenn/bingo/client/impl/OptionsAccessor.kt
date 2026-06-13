package me.jfenn.bingo.client.impl

import me.jfenn.bingo.client.platform.IOptionsAccessor
import net.minecraft.client.MinecraftClient

object OptionsAccessor : IOptionsAccessor {
    private val client = MinecraftClient.getInstance()

    override fun isDebugEnabled(): Boolean {
        return client.debugHudEntryList.isF3Enabled
    }

    override fun isPlayerListPressed(): Boolean {
        return client.options.playerListKey.isPressed
    }

    override fun isSneakPressed(): Boolean {
        return client.options.sneakKey.isPressed
    }

    override fun isHudHidden(): Boolean {
        return client.options.hudHidden
    }
}
