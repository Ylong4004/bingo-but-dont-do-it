package me.jfenn.bingo.integrations.ddi

import java.util.UUID

/**
 * 玩家 DDI 状态 — 追踪每个玩家的当前词条、心数、倒计时等。
 */
data class DDIPlayerState(
    val playerId: UUID,
    var currentWord: DDIWordPool.WordEntry? = null,
    var hearts: Int = 3,
    var maxHearts: Int = 3,
    var wordTimerSeconds: Int = 60,
    var maxWordTimerSeconds: Int = 60,
    var isEliminated: Boolean = false,
    /** 记录每种触发类型的上次触发 tick，用于冷却 */
    val lastTriggerTick: MutableMap<DDITriggerType, Long> = mutableMapOf(),
    /** 记录每个 triggerType 是否已触发过（用于一次性触发类） */
    val triggeredTypes: MutableSet<DDITriggerType> = mutableSetOf(),
) {
    val isAlive: Boolean get() = !isEliminated && hearts > 0

    fun loseHeart(): Boolean {
        hearts--
        return hearts <= 0
    }

    fun addHeart() {
        hearts++
    }

    fun assignWord(word: DDIWordPool.WordEntry, timerSeconds: Int) {
        currentWord = word
        wordTimerSeconds = timerSeconds
        maxWordTimerSeconds = timerSeconds
        triggeredTypes.clear()
        lastTriggerTick.clear()
    }

    fun reset() {
        currentWord = null
        hearts = maxHearts
        isEliminated = false
        wordTimerSeconds = maxWordTimerSeconds
        lastTriggerTick.clear()
        triggeredTypes.clear()
    }
}
