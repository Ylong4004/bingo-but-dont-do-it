package me.jfenn.bingo.impl

import me.jfenn.bingo.platform.ITextSerializer
import net.minecraft.text.Style
import net.minecraft.text.Text
import net.minecraft.text.TextColor
import net.minecraft.util.Formatting
import java.util.*

class TextSerializer : ITextSerializer {
    override fun toJson(text: Text): String {
        return Text.Serialization.toJsonString(text)
    }

    override fun fromJson(json: String): Text {
        return Text.Serialization.fromJson(json)!!
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
