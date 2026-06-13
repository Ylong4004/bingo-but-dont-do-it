package me.jfenn.bingo.common.utils

import org.slf4j.Logger
import kotlin.time.TimeSource

inline fun <R> Logger.measureTime(
    message: String,
    callback: () -> R,
): R {
    val start = TimeSource.Monotonic.markNow()
    info(message)

    return try {
        callback()
    } finally {
        info("$message - done in ${start.elapsedNow()}!")
    }
}