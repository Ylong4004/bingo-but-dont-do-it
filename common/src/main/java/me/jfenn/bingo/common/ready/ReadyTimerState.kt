package me.jfenn.bingo.common.ready

import me.jfenn.bingo.common.utils.minus
import me.jfenn.bingo.platform.IPlayerHandle
import java.time.Duration
import java.time.Instant
import java.util.*

class ReadyTimerState {

    private var totalPlayers = listOf<UUID>()
    private val readyPlayers = mutableSetOf<UUID>()
    private var startedAt: Instant? = null
    private var totalDuration: Duration? = null

    /**
     * If the timer is cancelled with /ready cancel
     * Don't start it again until /ready is used
     */
    var isCancelled: Boolean = false

    val isRunning: Boolean get() = startedAt != null

    fun updatePlayers(players: List<IPlayerHandle>) {
        totalPlayers = players.map { it.uuid }
    }

    fun isReady(player: UUID): Boolean = player in readyPlayers

    fun setReady(player: UUID, ready: Boolean) {
        if (ready) {
            readyPlayers.add(player)
        } else {
            readyPlayers.remove(player)
        }
    }

    fun startTimer(duration: Duration) {
        isCancelled = false
        startedAt = Instant.now()
        totalDuration = duration
    }

    fun duration(): Duration? {
        val readyRatio = totalPlayers.count { isReady(it) } / totalPlayers.size.toFloat().coerceAtLeast(1f)
        val millis = (1f - readyRatio) * (totalDuration?.toMillis() ?: return null)
        return Duration.ofMillis(millis.toLong())
    }

    fun totalTime(): Duration? = totalDuration

    fun elapsedTime(): Duration? {
        val now = Instant.now()
        return now - (startedAt ?: return null)
    }

    fun remainingTime(): Duration? {
        val totalDuration = duration() ?: return null
        val elapsedDuration = elapsedTime() ?: return null
        return totalDuration - elapsedDuration
    }

    fun reset() {
        readyPlayers.clear()
        startedAt = null
        totalDuration = null
        isCancelled = false
    }

}