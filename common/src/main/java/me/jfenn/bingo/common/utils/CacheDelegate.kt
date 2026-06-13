package me.jfenn.bingo.common.utils

import java.lang.ref.SoftReference
import java.time.Duration
import java.time.Instant

class CacheDelegateArg<T, A>(
    private val timeout: Duration,
    private val getter: (A) -> T,
) {
    private var lastExecution: Instant? = null
    private var lastValue: SoftReference<T>? = null

    fun get(arg: A): T {
        val now = Instant.now()
        if (lastExecution?.let { it + timeout > now } == true) {
            lastExecution = now
            lastValue?.get()?.let { return it }
        }

        val value = getter(arg)
        lastExecution = now
        lastValue = SoftReference(value)
        return value
    }
}

fun <T, A> cacheFor(timeout: Duration, getter: (A) -> T) = CacheDelegateArg(timeout, getter)
