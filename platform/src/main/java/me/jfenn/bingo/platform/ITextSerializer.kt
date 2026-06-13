package me.jfenn.bingo.platform

import net.minecraft.text.Text

interface ITextSerializer {
    fun toJson(text: Text): String
    fun fromJson(json: String): Text
    fun toRawString(text: Text): String
}