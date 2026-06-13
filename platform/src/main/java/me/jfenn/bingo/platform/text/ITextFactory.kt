package me.jfenn.bingo.platform.text

import kotlinx.serialization.Contextual
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import java.net.URI
import java.util.*

interface ITextFactory {
    fun empty(): IText
    fun from(text: Text): IText
    fun literal(str: String): IText
    fun keybind(key: String): IText
    fun translatable(key: String, fallback: String?, vararg args: Any): IText
    fun bracketedCopyable(str: String): IText
    fun player(uuid: UUID): IText? = null

    fun joinText(
        list: List<IText>,
        separator: IText = literal(", ")
    ): IText {
        val t = empty()
        list.forEachIndexed { index, text ->
            t.append(text)
            if (index < list.size-1) t.append(separator)
        }
        return t
    }
}

interface IText {
    val value: Text
    fun isEmpty(): Boolean
    fun formatted(vararg formatting: Formatting): IText
    fun setColor(color: Int)
    fun append(text: IText): IText
    fun append(str: String): IText
    fun setClickEvent(event: TextAction)
    fun setHoverEvent(event: HoverAction)
    fun resetStyle(): IText
    fun bracketed(): IText
    fun copy(): IText
}

typealias ITextSerialized = @Contextual IText

sealed interface TextAction {
    class OpenUrl(val url: URI): TextAction
    class RunCommand(val command: String): TextAction
    class SuggestCommand(val command: String): TextAction
}

sealed interface HoverAction {
    class ShowText(val text: IText): HoverAction
}