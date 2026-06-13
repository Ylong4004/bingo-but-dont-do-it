package me.jfenn.bingo.common.options

import kotlinx.serialization.Serializable
import me.jfenn.bingo.common.utils.DurationType

@Serializable
sealed interface RestoreOption {
    fun apply(options: BingoOptions)
}

@Serializable
data class RestoreGoal(val goal: BingoGoal) : RestoreOption {
    override fun apply(options: BingoOptions) {
        options.cards.forEach { it.goal = goal }
    }
}

@Serializable
data class RestoreStalemateBehavior(val stalemateBehavior: StalemateBehavior) : RestoreOption {
    override fun apply(options: BingoOptions) {
        options.stalemateBehavior = stalemateBehavior
    }
}

@Serializable
data class RestoreWinCondition(val winCondition: BingoWinCondition) : RestoreOption {
    override fun apply(options: BingoOptions) {
        options.winCondition = winCondition
    }
}

@Serializable
data class RestoreEndWhen(val endGameWhen: EndWhen) : RestoreOption {
    override fun apply(options: BingoOptions) {
        options.endGameWhen = endGameWhen
    }
}

@Serializable
data class RestoreTimeLimit(val timeLimit: DurationType?) : RestoreOption {
    override fun apply(options: BingoOptions) {
        options.timeLimit = timeLimit
    }
}
