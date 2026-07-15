package me.jfenn.bingo.integrations.ddi

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Converts vanilla's cumulative travel statistics into per-tick progress.
 *
 * A changed signal kind, a newly assigned word, or a statistic reset only
 * establishes a new baseline. This means persisted lifetime statistics can
 * never be mistaken for progress earned during the current DDI word.
 */
internal class DDITravelStatSampler {

    private data class Baseline(
        val signalKind: DDISignalKind,
        val centimetres: Int,
    )

    private val baselines = ConcurrentHashMap<UUID, Baseline>()

    fun sample(
        playerId: UUID,
        signalKind: DDISignalKind,
        currentCentimetres: Int,
    ): Int {
        val previous = baselines.put(
            playerId,
            Baseline(signalKind, currentCentimetres),
        ) ?: return 0

        if (previous.signalKind != signalKind || currentCentimetres <= previous.centimetres) {
            return 0
        }

        return (currentCentimetres.toLong() - previous.centimetres.toLong())
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
    }

    fun reset(playerId: UUID) {
        baselines.remove(playerId)
    }

    fun clear() {
        baselines.clear()
    }

    fun retainPlayers(playerIds: Set<UUID>) {
        baselines.keys.retainAll(playerIds)
    }
}
