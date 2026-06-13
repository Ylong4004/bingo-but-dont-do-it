package me.jfenn.bingo.common.card.objective

import me.jfenn.bingo.common.MDC_DEBUG
import me.jfenn.bingo.common.MDC_FILENAME
import me.jfenn.bingo.common.MDC_OBJECTIVE
import org.slf4j.Logger
import org.slf4j.MDC

internal fun Logger.objectiveError(
    id: String?,
    message: String,
    e: Throwable? = null,
    shouldPrint: Boolean = !MDC.get(MDC_DEBUG).isNullOrEmpty()
) {
    if (shouldPrint) {
        val filename = MDC.get(MDC_FILENAME)?.let { "[$it]" }
        val ids = listOfNotNull(MDC.get(MDC_OBJECTIVE), id)
            .distinct()
            .joinToString(" -> ")
            .takeIf { it.isNotEmpty() }
            ?.plus(":")
        val logMessage = listOfNotNull(filename, ids, message)
            .joinToString(" ")

        error(logMessage, e)
    }
}

internal inline fun <R> withMdc(
    vararg params: Pair<String, String>,
    callback: () -> R,
): R {
    return try {
        params.forEach { (key, value) -> MDC.pushByKey(key, value) }
        callback()
    } finally {
        params.forEach { (key, _) -> MDC.popByKey(key) }
    }
}