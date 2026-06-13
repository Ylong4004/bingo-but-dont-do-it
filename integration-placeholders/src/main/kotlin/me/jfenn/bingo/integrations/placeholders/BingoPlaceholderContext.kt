package me.jfenn.bingo.integrations.placeholders

import eu.pb4.placeholders.api.PlaceholderContext
import net.minecraft.server.MinecraftServer

class BingoPlaceholderContext(
    val context: PlaceholderContext,
) : IPlaceholderContext {
    override val server: MinecraftServer
        get() = context.server
}