package me.jfenn.bingo.common.utils

fun IntRange.formatString(): String {
    return when {
        first > 0 && last < Int.MAX_VALUE -> "$first-$last"
        last < Int.MAX_VALUE -> "<= $last"
        else -> "$first"
    }
}
