package me.jfenn.bingo.impl

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import me.jfenn.bingo.platform.ITextSerializer
import net.minecraft.text.Style
import net.minecraft.text.Text
import net.minecraft.text.TextCodecs
import net.minecraft.text.TextColor
import net.minecraft.util.Formatting
import java.util.*

class TextSerializer : ITextSerializer {

    private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()

    override fun toJson(text: Text): String {
        // this achieves Text.Serialization.toJsonString, but statically, without requiring RegistryWrapper
        val element = TextCodecs.CODEC.encodeStart(JsonOps.INSTANCE, text).getOrThrow()
        return gson.toJson(element)
    }

    override fun fromJson(json: String): Text {
        // this achieves Text.Serialization.fromJson, but statically, without requiring RegistryWrapper
        val element = JsonParser.parseString(json)
        return TextCodecs.CODEC.parse(JsonOps.INSTANCE, element).getOrThrow()
    }

    override fun toRawString(text: Text): String {
        val builder = StringBuilder()
        text.visit<Unit>(
            { style, asString ->
                if (style.isEmpty) builder.append(Formatting.RESET)
                if (style.isBold) builder.append(Formatting.BOLD)
                if (style.isItalic) builder.append(Formatting.ITALIC)
                if (style.isStrikethrough) builder.append(Formatting.STRIKETHROUGH)
                if (style.isUnderlined) builder.append(Formatting.UNDERLINE)
                if (style.isObfuscated) builder.append(Formatting.OBFUSCATED)

                Formatting.entries
                    .filter { it.isColor }
                    .find {
                        val textColor = TextColor.fromFormatting(it)
                        textColor != null && style.color?.rgb == textColor.rgb
                    }
                    ?.also { builder.append(it) }

                builder.append(asString)
                Optional.empty()
            },
            Style.EMPTY
        )
        return builder.toString()
    }

}
