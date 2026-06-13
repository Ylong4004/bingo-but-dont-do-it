package me.jfenn.bingo.common.stats

/**
 * Formats a number with commas
 */
fun Long.formatLargeNumber(): String {
    return String.format("%,d", this)
}
