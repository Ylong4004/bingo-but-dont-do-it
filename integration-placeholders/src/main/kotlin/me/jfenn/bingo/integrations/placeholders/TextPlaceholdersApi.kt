package me.jfenn.bingo.integrations.placeholders

import eu.pb4.placeholders.api.PlaceholderContext
import eu.pb4.placeholders.api.PlaceholderHandler
import eu.pb4.placeholders.api.PlaceholderResult
import eu.pb4.placeholders.api.Placeholders
import eu.pb4.placeholders.api.parsers.PatternPlaceholderParser
import net.minecraft.server.MinecraftServer
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import java.util.function.Function
import java.util.function.Supplier

class TextPlaceholdersApi : ITextPlaceholdersApi {
    override fun registerPlaceholder(id: String, handler: Function<IPlaceholderContext, Text>) {
        Placeholders.register(Identifier.of("yet-another-minecraft-bingo", id)!!) { ctx, _ ->
            try {
                handler.apply(BingoPlaceholderContext(ctx))
                    .let { PlaceholderResult.value(it) }
            } catch (e: Throwable) {
                PlaceholderResult.invalid("${e::class.simpleName}: ${e.message}")
            }
        }
    }

    override fun parseText(message: Text, server: MinecraftServer): Text {
        return Placeholders.parseText(message, PlaceholderContext.of(server))
    }

    override fun parseInline(message: Text, server: MinecraftServer, replacements: Map<String, Supplier<Text>>): Text {
        return Placeholders.parseText(
            message,
            PlaceholderContext.of(server),
            PatternPlaceholderParser.PLACEHOLDER_PATTERN_CUSTOM,
            PlaceholderGetter(replacements),
        )
    }
}

class PlaceholderGetter(
    private val placeholders: Map<String, Supplier<Text>>,
) : Placeholders.PlaceholderGetter {
    override fun getPlaceholder(placeholder: String?): PlaceholderHandler? {
        return placeholders[placeholder]
            ?.let { BingoPlaceholderHandler(it) }
    }
}

class BingoPlaceholderHandler(
    private val supplier: Supplier<Text>,
) : PlaceholderHandler {
    override fun onPlaceholderRequest(context: PlaceholderContext?, argument: String?): PlaceholderResult {
        return try {
            PlaceholderResult.value(supplier.get())
        } catch (e: Throwable) {
            PlaceholderResult.invalid("${e::class.simpleName}: ${e.message}")
        }
    }
}
