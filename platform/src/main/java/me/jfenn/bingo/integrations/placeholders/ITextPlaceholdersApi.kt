package me.jfenn.bingo.integrations.placeholders

import net.minecraft.server.MinecraftServer
import net.minecraft.text.Text
import java.util.function.Function
import java.util.function.Supplier

interface ITextPlaceholdersApi {
    fun registerPlaceholder(id: String, handler: Function<IPlaceholderContext, Text>)
    fun parseText(message: Text, server: MinecraftServer): Text
    fun parseInline(message: Text, server: MinecraftServer, replacements: Map<String, Supplier<Text>>): Text
}

interface IPlaceholderContext {
    val server: MinecraftServer
}
