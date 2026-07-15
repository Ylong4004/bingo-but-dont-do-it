package me.jfenn.bingo.integrations.ddi

/** Pure rule evaluation helpers kept separate from server lifecycle code. */
object DDIRuleEngine {

    data class ProgressResult(
        val progress: Int,
        val completed: Boolean,
    )

    fun apply(
        rule: DDIRuleDefinition,
        currentProgress: Int,
        signal: DDISignal,
    ): ProgressResult? {
        if (!rule.matches(signal)) return null
        val progress = (currentProgress + rule.contribution(signal))
            .coerceAtMost(rule.requiredProgress)
        return ProgressResult(
            progress = progress,
            completed = progress >= rule.requiredProgress,
        )
    }
}
