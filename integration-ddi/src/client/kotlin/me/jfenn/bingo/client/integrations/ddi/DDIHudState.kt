package me.jfenn.bingo.client.integrations.ddi

import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * 客户端 DDI HUD 状态 — 存储当前玩家的词条、心数和其他玩家的 DDI 状态。
 */
class DDIHudState {
    /** 当前客户端玩家的 DDI 词条 */
    var myWordText: String = ""
    var myHearts: Int = 0
    var myMaxHearts: Int = 0
    var myTimerSeconds: Int = 0
    var myMaxTimerSeconds: Int = 0
    var isMyEliminated: Boolean = false

    /** 其他玩家的 DDI 状态（用于显示） */
    data class PlayerDDIInfo(
        val playerName: String,
        val wordText: String,
        var hearts: Int,
        var maxHearts: Int,
        var timerSeconds: Int,
        var isEliminated: Boolean,
    )

    val otherPlayers = ConcurrentHashMap<UUID, PlayerDDIInfo>()

    /** 最近触发通知（用于闪烁显示） */
    data class TriggerNotification(
        val playerName: String,
        val wordText: String,
        var remainingHearts: Int,
        val isElimination: Boolean,
        val isGain: Boolean,
        var timeAliveMs: Long = 0L,
    ) {
        fun isExpired(): Boolean = timeAliveMs > 4000 // 4 seconds
    }

    val recentTriggers = mutableListOf<TriggerNotification>()

    /** 是否显示 DDI HUD */
    var isVisible: Boolean = false

    fun reset() {
        myWordText = ""
        myHearts = 0
        myMaxHearts = 0
        myTimerSeconds = 0
        myMaxTimerSeconds = 0
        isMyEliminated = false
        otherPlayers.clear()
        recentTriggers.clear()
        isVisible = false
    }
}
