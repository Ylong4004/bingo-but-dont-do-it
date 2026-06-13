package me.jfenn.bingo.client.platform.renderer

import me.jfenn.bingo.platform.text.IText

interface IFont {
    fun getTextWidth(text: IText): Int
    fun getTextHeight(): Int
    fun wrapLines(text: IText, width: Int): List<IText>
    fun truncate(text: IText, width: Int): IText
}