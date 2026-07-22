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
 * 一个目标内独立结算的一条禁做词。
 *
 * 词条、倒计时、规则进度和异步识别版本号都属于槽位；生命和淘汰状态则属于
 * [DDIObjectiveState]。这个拆分确保同一次动作可以命中多个槽位，却不会让一个
 * 槽位的换词使另一个槽位的异步结果或倒计时失效。
 */
data class DDIWordSlotState(
    val index: Int,
    var currentWord: DDIWordPool.WordEntry? = null,
    var wordTimerSeconds: Int = 0,
    var maxWordTimerSeconds: Int = 0,
    /** 当前所分配参数化规则的进度。 */
    var ruleProgress: Int = 0,
    /** 本次 ON_DEADLINE_MISSED 规则一旦满足即为 true。 */
    var deadlineSatisfied: Boolean = false,
    /** 拒绝同一服务端游戏刻内同一槽位的重复回调。 */
    var lastAcceptedTriggerTick: Long = Long.MIN_VALUE,
    /** 每次发放和清除都会递增，供异步语音结果验证。 */
    var assignmentRevision: Long = 0,
) {
    init {
        require(index >= 0) { "DDI slot index cannot be negative" }
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
        maxWordTimerSeconds = 0
        ruleProgress = 0
        deadlineSatisfied = false
    }
}

/**
 * 由单个玩家或一支 Bingo 队伍持有的服务端权威状态。
 *
 * 队伍共享模式会为每支 Bingo 队伍仅创建一个目标，因此队员之间的词条槽位和
 * 生命池始终保持一致。
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
    var hearts: Int = 3,
    var maxHearts: Int = 3,
    var isEliminated: Boolean = false,
    val slots: MutableList<DDIWordSlotState> = mutableListOf(DDIWordSlotState(index = 0)),
) {
    val isAlive: Boolean get() = !isEliminated && hearts > 0

    init {
        require(slots.isNotEmpty()) { "A DDI objective requires at least one word slot" }
        require(slots.map(DDIWordSlotState::index).distinct().size == slots.size) {
            "DDI objective slot indices must be unique"
        }
    }

    fun loseHeart(): Boolean {
        hearts = (hearts - 1).coerceAtLeast(0)
        return hearts <= 0
    }

    fun addHeart() {
        hearts = (hearts + 1).coerceAtMost(maxHearts)
    }

    fun slot(index: Int): DDIWordSlotState? = slots.firstOrNull { it.index == index }

    fun activeSlots(): List<DDIWordSlotState> = slots.filter { it.currentWord != null }
}
