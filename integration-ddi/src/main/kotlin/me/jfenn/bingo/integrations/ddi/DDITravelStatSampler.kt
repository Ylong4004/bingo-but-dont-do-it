package me.jfenn.bingo.integrations.ddi

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 将原版累计移动统计转换为每刻进度。
 *
 * 信号类型变化、新词条分配或统计值重置时只会建立新的基线。
 * 因此，持久化的生涯统计不会被误认为当前 DDI 词条期间取得的进度。
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
