package me.jfenn.bingo.client.common.utils

import me.jfenn.bingo.common.utils.div
import me.jfenn.bingo.common.utils.minus
import java.time.Duration
import java.time.Instant
import kotlin.math.PI
import kotlin.math.cos

class Interpolate(
    var from: Float,
    var to: Float,
    var duration: Duration = Duration.ZERO,
    var startedAt: Instant = Instant.now(),
) {
    enum class Easing {
        LINEAR,
        IN_OUT
    }

    fun progress(
        easing: Easing = Easing.LINEAR,
        now: Instant = Instant.now(),
    ): Float {
        val animTimeSince = now - startedAt
        val progress = (animTimeSince / duration).toFloat().coerceIn(0f, 1f)
        return when (easing) {
            Easing.LINEAR -> progress
            Easing.IN_OUT -> -(cos(PI * progress()) - 1).toFloat() / 2f
        }
    }

    fun isDone() = progress() > 0.9999f

    fun get(
        easing: Easing = Easing.LINEAR,
        now: Instant = Instant.now(),
    ): Float {
        val progress = progress(easing, now)
        return (from * (1f - progress)) + (to * progress)
    }

    private var isFinished = false
    fun update(): Boolean {
        val ret = !isFinished
        isFinished = isDone()
        return ret
    }
}