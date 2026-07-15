package me.jfenn.bingo.integrations.ddi

/** Stable event kinds understood by the parameterized DDI rule engine. */
enum class DDISignalKind {
    LEGACY,
    ITEM_CRAFTED,
    ITEM_PICKED_UP,
    ITEM_DROPPED,
    BLOCK_BROKEN,
    BLOCK_PLACED,
    BLOCK_STOOD_ON,
    ITEM_HELD,
    /** A tile was accepted by Bingo's authoritative score transaction. */
    BINGO_TILE_CAPTURED,
    /** Vanilla server travel statistics, expressed in centimetres. */
    DISTANCE_WALKED_CM,
    DISTANCE_SPRINTED_CM,
    DISTANCE_SWUM_CM,
    DISTANCE_BOAT_CM,
    /** Final, non-zero damage dealt to a player on another Bingo team. */
    ENEMY_PLAYER_DAMAGED,
    /** Final, non-zero damage received from a player on another Bingo team. */
    DAMAGED_BY_ENEMY_PLAYER,
}

/** Defines how a matching signal contributes to a multi-action rule. */
enum class DDIProgressMode {
    ACTIONS,
    QUANTITY,
}

/** Defines what happens when the visible word timer reaches zero. */
enum class DDIDeadlineBehavior {
    /** The current behaviour: replace the word without deducting a heart. */
    REROLL,

    /** The deadline itself violates the rule and deducts a heart. */
    TRIGGER_ON_EXPIRY,
}

/** Defines whether a completed match is itself forbidden or satisfies a deadline. */
enum class DDIMatchBehavior {
    /** The usual Don't Do It rule: completing the match deducts a heart. */
    VIOLATION,

    /** Matching makes the objective safe; expiry deducts only if no match occurred. */
    SATISFY_DEADLINE,
}

/**
 * One authoritative gameplay observation.
 *
 * [subjectId] and [subjectTags] always contain full namespaced identifiers.
 * A physical action is represented by one signal, while [legacyAliases]
 * preserves every old enum interpretation of that action.
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
 * Data-oriented rule evaluated against only the current objective's signal.
 * Empty subject sets mean "any subject"; otherwise an exact ID or tag match
 * is required. This keeps event handling O(1) with respect to pool size.
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

    /** Compact, non-localized representation for the operator reveal command. */
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
