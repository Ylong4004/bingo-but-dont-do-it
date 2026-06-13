package me.jfenn.bingo.integrations.placeholders

import net.minecraft.server.MinecraftServer
import net.minecraft.text.Text
import java.util.function.Function
import java.util.function.Supplier

object DummyPlaceholders : ITextPlaceholdersApi {
    override fun registerPlaceholder(id: String, handler: Function<IPlaceholderContext, Text>) {}

    override fun parseText(message: Text, server: MinecraftServer): Text = message

    override fun parseInline(message: Text, server: MinecraftServer, replacements: Map<String, Supplier<Text>>): Text = message
}