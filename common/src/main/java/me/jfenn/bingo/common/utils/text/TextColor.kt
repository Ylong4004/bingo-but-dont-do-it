package me.jfenn.bingo.common.utils.text

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class TextColor(val isDark: Boolean) {
    @SerialName("black") BLACK(true),
    @SerialName("dark_blue") DARK_BLUE(true),
    @SerialName("dark_green") DARK_GREEN(true),
    @SerialName("dark_aqua") DARK_AQUA(true),
    @SerialName("dark_red") DARK_RED(true),
    @SerialName("dark_purple") DARK_PURPLE(true),
    @SerialName("gold") GOLD(false),
    @SerialName("gray") GRAY(false),
    @SerialName("dark_gray") DARK_GRAY(true),
    @SerialName("blue") BLUE(true),
    @SerialName("green") GREEN(false),
    @SerialName("aqua") AQUA(false),
    @SerialName("red") RED(true),
    @SerialName("light_purple") LIGHT_PURPLE(false),
    @SerialName("yellow") YELLOW(false),
    @SerialName("white") WHITE(false),
}