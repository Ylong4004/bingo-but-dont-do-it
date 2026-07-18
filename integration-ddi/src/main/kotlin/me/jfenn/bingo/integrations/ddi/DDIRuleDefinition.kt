package me.jfenn.bingo.integrations.ddi

/** 参数化 DDI 规则引擎所识别的稳定事件类型。 */
enum class DDISignalKind {
    LEGACY,
    ITEM_CRAFTED,
    ITEM_PICKED_UP,
    ITEM_DROPPED,
    BLOCK_BROKEN,
    BLOCK_PLACED,
    BLOCK_STOOD_ON,
    ITEM_HELD,
    /** 一个格子已被 Bingo 的权威计分事务接受。 */
    BINGO_TILE_CAPTURED,
    /** 原版服务端移动统计数据，以厘米为单位。 */
    DISTANCE_WALKED_CM,
    DISTANCE_SPRINTED_CM,
    DISTANCE_SWUM_CM,
    DISTANCE_BOAT_CM,
    /** 对另一支 Bingo 队伍的玩家造成的最终非零伤害。 */
    ENEMY_PLAYER_DAMAGED,
    /** 从另一支 Bingo 队伍的玩家处受到的最终非零伤害。 */
    DAMAGED_BY_ENEMY_PLAYER,
    /** 已同意语音识别的玩家，其本地 ASR 最终语句匹配了关键词。 */
    VOICE_KEYWORD_SPOKEN,
}

/** 定义匹配信号如何为多动作规则贡献进度。 */
enum class DDIProgressMode {
    ACTIONS,
    QUANTITY,
}

/** 定义可见词条计时器归零时的行为。 */
enum class DDIDeadlineBehavior {
    /** 当前行为：更换词条，但不扣除生命。 */
    REROLL,

    /** 截止时间本身即构成规则违规，并扣除一颗生命。 */
    TRIGGER_ON_EXPIRY,
}

/** 定义完成匹配本身是被禁止的行为，还是满足限时要求。 */
enum class DDIMatchBehavior {
    /** 常规的“不要做”规则：完成匹配会扣除一颗生命。 */
    VIOLATION,

    /** 匹配后目标即安全；只有到期前未匹配时才会扣除生命。 */
    SATISFY_DEADLINE,
}

/**
 * 一次权威的游戏行为观测。
 *
 * [subjectId] 和 [subjectTags] 始终包含完整的命名空间标识符。
 * 一个实际动作由单个信号表示，而 [legacyAliases]
 * 会保留该动作在旧枚举中的所有解释方式。
 */
data class DDISignal(
    val kind: DDISignalKind,
    val subjectId: String? = null,
    val subjectTags: Set<String> = emptySet(),
    val quantity: Int = 1,
    val isBlockItem: Boolean = false,
    val legacyAliases: Set<DDITriggerType> = emptySet(),
) {
    init {
        require(quantity > 0) { "DDI signal quantity must be positive" }
    }

    companion object {
        fun legacy(type: DDITriggerType): DDISignal = DDISignal(
            kind = DDISignalKind.LEGACY,
            legacyAliases = setOf(type),
        )
    }
}

/**
 * 面向数据的规则，仅根据当前目标收到的信号进行判定。
 * 空主体集合表示“任意主体”；否则必须精确匹配 ID 或标签。
 * 这样可以使事件处理相对于词池大小保持 O(1) 复杂度。
 */
data class DDIRuleDefinition(
    val signalKind: DDISignalKind,
    val legacyTrigger: DDITriggerType? = null,
    val subjectIds: Set<String> = emptySet(),
    val subjectTags: Set<String> = emptySet(),
    val requireBlockItem: Boolean? = null,
    val requiredProgress: Int = 1,
    val progressMode: DDIProgressMode = DDIProgressMode.ACTIONS,
    val deadlineBehavior: DDIDeadlineBehavior = DDIDeadlineBehavior.REROLL,
    val matchBehavior: DDIMatchBehavior = DDIMatchBehavior.VIOLATION,
    val requiredMods: Set<String> = emptySet(),
) {
    init {
        require(requiredProgress > 0) { "DDI rule progress target must be positive" }
        require(signalKind != DDISignalKind.LEGACY || legacyTrigger != null) {
            "A legacy DDI rule requires a legacy trigger"
        }
        require(subjectIds.none { ':' !in it }) {
            "DDI subject IDs must be namespaced: $subjectIds"
        }
        require(subjectTags.none { ':' !in it }) {
            "DDI subject tag IDs must be namespaced: $subjectTags"
        }
        require(requiredMods.none(String::isBlank)) { "DDI required mod IDs cannot be blank" }
        require(matchBehavior != DDIMatchBehavior.SATISFY_DEADLINE ||
            deadlineBehavior == DDIDeadlineBehavior.TRIGGER_ON_EXPIRY) {
            "A deadline-satisfaction rule must trigger when its deadline expires"
        }
    }

    fun matches(signal: DDISignal): Boolean {
        if (signalKind == DDISignalKind.LEGACY) {
            return legacyTrigger in signal.legacyAliases
        }
        if (signal.kind != signalKind) return false
        if (requireBlockItem != null && signal.isBlockItem != requireBlockItem) return false

        val hasSubjectPredicate = subjectIds.isNotEmpty() || subjectTags.isNotEmpty()
        if (!hasSubjectPredicate) return true
        if (signal.subjectId != null && signal.subjectId in subjectIds) return true
        return signal.subjectTags.any(subjectTags::contains)
    }

    fun contribution(signal: DDISignal): Int = when (progressMode) {
        DDIProgressMode.ACTIONS -> 1
        DDIProgressMode.QUANTITY -> signal.quantity
    }

    fun isAvailable(isModLoaded: (String) -> Boolean): Boolean =
        requiredMods.all(isModLoaded)

    /** 供管理员揭示命令使用的紧凑、非本地化表示。 */
    fun diagnosticName(): String = buildString {
        append(signalKind.name)
        legacyTrigger?.let { append(':').append(it.name) }
        if (subjectIds.isNotEmpty()) append(":id=").append(subjectIds.sorted().joinToString("|"))
        if (subjectTags.isNotEmpty()) append(":tag=").append(subjectTags.sorted().joinToString("|"))
        if (requireBlockItem != null) append(":blockItem=").append(requireBlockItem)
        if (requiredProgress > 1) {
            append(":progress=").append(requiredProgress).append('/').append(progressMode.name)
        }
        if (deadlineBehavior != DDIDeadlineBehavior.REROLL) {
            append(":deadline=").append(deadlineBehavior.name)
        }
        if (matchBehavior != DDIMatchBehavior.VIOLATION) {
            append(":match=").append(matchBehavior.name)
        }
    }

    companion object {
        fun legacy(type: DDITriggerType): DDIRuleDefinition = DDIRuleDefinition(
            signalKind = DDISignalKind.LEGACY,
            legacyTrigger = type,
        )
    }
}
