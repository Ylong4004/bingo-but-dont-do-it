package me.jfenn.bingo.common.utils

fun String.formatTitle(): String {
    return this
        .replace(Regex("([a-z][A-Z])")) { it.value[0] + " " + it.value[1] }
        .replace('_', ' ')
        .replaceFirstChar { it.uppercase() }
        .replace(Regex("(\\s[a-z])")) { it.value.uppercase() }
}