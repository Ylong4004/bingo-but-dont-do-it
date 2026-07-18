package me.jfenn.bingo.integrations.ddi

import me.jfenn.bingo.common.team.BingoTeamKey
import net.minecraft.util.Formatting
import java.util.UUID

/** DDI 名单中的固定参与者；可变的游戏状态保存在目标上。 */
data class DDIPlayerState(
    val playerId: UUID,
    val playerName: String,
    val teamKey: BingoTeamKey,
)

/**
 * 由单个玩家或一支 Bingo 队伍持有的服务端权威状态。
 *
 * 队伍共享模式会为每支 Bingo 队伍仅创建一个目标，因此队员之间的词条、
 * 计时器和生命池始终保持一致。
 */
data class DDIObjectiveState(
    val objectiveId: String,
    val objectiveName: String,
    val teamKey: BingoTeamKey,
    val teamName: String,
    val teamColor: Formatting,
    val memberIds: Set<UUID>,
    val memberNames: List<String>,
    val isTeamShared: Boolean,
    var currentWord: DDIWordPool.WordEntry? = null,
    var hearts: Int = 3,
    var maxHearts: Int = 3,
    var wordTimerSeconds: Int = 60,
    var maxWordTimerSeconds: Int = 60,
    var isEliminated: Boolean = false,
    /** 当前所分配参数化规则的共享进度。 */
    var ruleProgress: Int = 0,
    /** 本次分配的 ON_DEADLINE_MISSED 规则一旦满足即为 true。 */
    var deadlineSatisfied: Boolean = false,
    /** 在词条更换时保留，用于拒绝同一服务端游戏刻内的第二次回调。 */
    var lastAcceptedTriggerTick: Long = Long.MIN_VALUE,
    /**
     * 供异步检测器使用的单调递增令牌。每次发放和清除时都会变化，
     * 即使未来词池再次抽到相同的词条 ID 也不例外。
     */
    var assignmentRevision: Long = 0,
) {
    val isAlive: Boolean get() = !isEliminated && hearts > 0

    fun loseHeart(): Boolean {
        hearts = (hearts - 1).coerceAtLeast(0)
        return hearts <= 0
    }

    fun addHeart() {
        hearts = (hearts + 1).coerceAtMost(maxHearts)
    }

    fun assignWord(word: DDIWordPool.WordEntry, timerSeconds: Int) {
        assignmentRevision++
        currentWord = word
        wordTimerSeconds = timerSeconds
        maxWordTimerSeconds = timerSeconds
        ruleProgress = 0
        deadlineSatisfied = false
    }

    fun clearWord() {
        assignmentRevision++
        currentWord = null
        wordTimerSeconds = 0
        ruleProgress = 0
        deadlineSatisfied = false
    }
}
