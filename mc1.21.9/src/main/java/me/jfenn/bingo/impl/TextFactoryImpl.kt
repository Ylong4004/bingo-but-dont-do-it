package me.jfenn.bingo.impl

import me.jfenn.bingo.platform.text.HoverAction
import me.jfenn.bingo.platform.text.IText
import me.jfenn.bingo.platform.text.ITextFactory
import me.jfenn.bingo.platform.text.TextAction
import net.minecraft.component.type.ProfileComponent
import net.minecraft.text.*
import net.minecraft.text.`object`.PlayerTextObjectContents
import net.minecraft.util.Formatting
import java.util.*

class TextFactoryImpl : ITextFactory {
    override fun empty(): IText {
        return TextImpl(Text.empty())
    }

    override fun from(text: Text): IText {
        return TextImpl(if (text is MutableText) text else text.copy())
    }

    override fun literal(str: String): IText {
        return TextImpl(Text.literal(str))
    }

    override fun keybind(key: String): IText {
        return TextImpl(Text.keybind(key))
    }

    override fun translatable(key: String, fallback: String?, vararg args: Any): IText {
        val mappedArgs = args.map {
            when (it) {
                is IText -> it.value
                else -> it
            }
        }
        return TextImpl(Text.translatableWithFallback(key, fallback, *mappedArgs.toTypedArray()))
    }

    override fun bracketedCopyable(str: String): IText {
        return TextImpl(Texts.bracketedCopyable(str))
    }

    override fun player(uuid: UUID): IText {
        return TextImpl(
            Text.`object`(
                PlayerTextObjectContents(
                    ProfileComponent.ofDynamic(uuid),
                    true
                )
            )
        )
    }
}

class TextImpl(
    override val value: MutableText
) : IText {
    override fun isEmpty(): Boolean {
        return value.string.isEmpty()
    }

    override fun setClickEvent(event: TextAction) {
        value.setStyle(
            value.style.withClickEvent(
                when (event) {
                    is TextAction.OpenUrl -> ClickEvent.OpenUrl(event.url)
                    is TextAction.RunCommand -> ClickEvent.RunCommand(event.command)
                    is TextAction.SuggestCommand -> ClickEvent.SuggestCommand(event.command)
                }
            )
        )
    }

    override fun setHoverEvent(event: HoverAction) {
        value.setStyle(
            value.style.withHoverEvent(
                when (event) {
                    is HoverAction.ShowText -> {
                        val text = event.text
                        require(text is TextImpl)
                        HoverEvent.ShowText(text.value)
                    }
                }
            )
        )
    }

    override fun resetStyle(): IText {
        value.setStyle(Style.EMPTY.withItalic(false))
        return this
    }

    override fun bracketed(): IText {
        return TextImpl(Texts.bracketed(value))
    }

    override fun formatted(vararg formatting: Formatting): IText {
        value.formatted(*formatting)
        return this
    }

    override fun setColor(color: Int) {
        value.styled { it.withColor(color) }
    }

    override fun append(text: IText): IText {
        require(text is TextImpl)
        value.append(text.value)
        return this
    }

    override fun append(str: String): IText {
        value.append(str)
        return this
    }

    override fun equals(other: Any?): Boolean = other is TextImpl && value == other.value
    override fun hashCode(): Int = value.hashCode()
    override fun toString(): String = value.string

    override fun copy() = TextImpl(value.copy())
}