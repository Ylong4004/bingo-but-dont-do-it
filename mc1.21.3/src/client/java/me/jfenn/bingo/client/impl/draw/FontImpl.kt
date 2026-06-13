package me.jfenn.bingo.client.impl.draw

import me.jfenn.bingo.client.platform.renderer.IFont
import me.jfenn.bingo.platform.text.IText
import me.jfenn.bingo.impl.TextImpl
import net.minecraft.client.font.TextRenderer
import net.minecraft.text.OrderedText
import net.minecraft.text.StringVisitable
import net.minecraft.text.Style
import net.minecraft.text.Text
import java.util.*

class FontImpl(
    private val textRenderer: TextRenderer
): IFont {
    override fun getTextWidth(text: IText): Int {
        return textRenderer.getWidth(text.value)
    }

    override fun getTextHeight(): Int {
        return textRenderer.fontHeight
    }

    override fun wrapLines(text: IText, width: Int): List<IText> {
        return textRenderer.wrapLines(text.value, width)
            .map { createText(it) }
    }

    override fun truncate(text: IText, width: Int): IText {
        return createText(textRenderer.trimToWidth(text.value, width))
    }

    private fun createText(visitable: StringVisitable): IText {
        val line = Text.empty()
        val curStr = StringBuilder()
        var curStyle: Style = Style.EMPTY
        visitable.visit(
            { style, asString ->
                if (style != curStyle) {
                    line.append(Text.literal(curStr.toString()).setStyle(curStyle))
                    curStr.clear()
                    curStyle = style
                }
                curStr.append(asString)
                Optional.empty<Unit>()
            },
            Style.EMPTY
        )
        line.append(Text.literal(curStr.toString()).setStyle(curStyle))
        return TextImpl(line)
    }

    private fun createText(orderedText: OrderedText): IText {
        val line = Text.empty()
        val curStr = StringBuilder()
        var curStyle: Style = Style.EMPTY
        orderedText.accept { _, style, codePoint ->
            if (style != curStyle) {
                line.append(Text.literal(curStr.toString()).setStyle(curStyle))
                curStr.clear()
                curStyle = style
            }
            curStr.appendCodePoint(codePoint)
            true
        }
        line.append(Text.literal(curStr.toString()).setStyle(curStyle))
        return TextImpl(line)
    }
}