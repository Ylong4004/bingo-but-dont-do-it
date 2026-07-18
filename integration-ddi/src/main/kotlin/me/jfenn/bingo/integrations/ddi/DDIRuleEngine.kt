package me.jfenn.bingo.integrations.ddi

/** 与服务端生命周期代码分离的纯规则求值辅助工具。 */
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
