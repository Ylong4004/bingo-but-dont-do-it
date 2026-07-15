package me.jfenn.bingo.integrations.ddi

import me.jfenn.bingo.common.team.BingoTeamKey

/** Per-round history shared by every DDI objective belonging to a Bingo team. */
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
