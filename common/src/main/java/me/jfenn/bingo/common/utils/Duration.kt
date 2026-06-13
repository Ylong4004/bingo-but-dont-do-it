package me.jfenn.bingo.common.utils

import java.time.Duration
import java.time.Instant
import java.util.*

/**
 * Returns a human-readable length of the duration, in the format:
 * "3d 2h 01m 32s"
 *
 * Omits the days/hours portion of the string if <= 0.
 */
fun Duration.formatString(): String {
    val sign = if (isNegative) "-" else ""
    val abs = abs()
    val days = abs.toDays()
    val hours = abs.toHoursPart()
    val minutes = abs.toMinutesPart()
    val seconds = abs.toSecondsPart()

    return (
            sign
                    + "${days}d ".takeIf { days > 0 }.orEmpty()
                    + "${hours}h ".takeIf { hours > 0 }.orEmpty()
                    + String.format(Locale.US, "%02dm %02ds", minutes, seconds)
            )
}

/**
 * Returns a human-readable length of the duration, in the format:
 * "2h" or "1m" or "1s", depending on the largest unit
 */
fun Duration.formatStringSmall(): String {
    val sign = if (isNegative) "-" else ""
    val abs = abs()
    val days = abs.toDays()
    val hours = abs.toHoursPart()
    val minutes = abs.toMinutesPart()
    val seconds = abs.toSecondsPart()
    val nanoseconds = abs.toNanosPart()

    return sign + when {
        days > 0 -> "${days}d"
        hours > 0 -> "${hours}h"
        minutes > 0 -> "${minutes}m"
        seconds > 0 -> "${seconds}s"
        else -> String.format(Locale.US, "%.3fs", nanoseconds.toDouble() / Duration.ofSeconds(1).toNanos())
    }
}

fun Duration.formatHHMMSS(): String {
    val sign = if (isNegative) "-" else ""
    val abs = abs()
    val hours = abs.toHours()
    val minutes = abs.toMinutesPart()
    val seconds = abs.toSecondsPart()

    return sign + when {
        hours > 0 -> String.format("%d:%02d:%02d", hours, minutes, seconds)
        else -> String.format("%d:%02d", minutes, seconds)
    }
}

inline val Int.milliseconds: Duration get() = Duration.ofMillis(this.toLong())
inline val Int.seconds: Duration get() = Duration.ofSeconds(this.toLong())
inline val Int.minutes: Duration get() = Duration.ofMinutes(this.toLong())
inline val Int.hours: Duration get() = Duration.ofHours(this.toLong())

operator fun Instant.minus(from: Instant): Duration = Duration.between(from, this)
operator fun Duration.div(other: Duration) = try {
    toMillis().toDouble() / other.toMillis().toDouble()
} catch (_: ArithmeticException) {
    0.0
}