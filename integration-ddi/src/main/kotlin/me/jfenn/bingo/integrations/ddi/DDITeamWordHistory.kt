package me.jfenn.bingo.integrations.ddi

import me.jfenn.bingo.common.team.BingoTeamKey

/** 同一 Bingo 队伍下所有 DDI 目标共享的单局历史记录。 */
internal class DDITeamWordHistory {
    private val triggeredRepeatKeys = mutableMapOf<BingoTeamKey, MutableSet<String>>()

    fun record(teamKey: BingoTeamKey, word: DDIWordPool.WordEntry) {
        triggeredRepeatKeys.getOrPut(teamKey, ::linkedSetOf).add(word.repeatKey)
    }

    fun get(teamKey: BingoTeamKey): Set<String> =
        triggeredRepeatKeys[teamKey]?.toSet().orEmpty()

    fun reset() {
        triggeredRepeatKeys.clear()
    }
}
