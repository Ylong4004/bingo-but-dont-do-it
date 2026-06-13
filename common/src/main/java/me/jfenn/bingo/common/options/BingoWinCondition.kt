package me.jfenn.bingo.common.options

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface BingoWinCondition {
    @Serializable
    @SerialName("cards")
    data class Cards(val cards: Int): BingoWinCondition
    @Serializable
    @SerialName("infinite")
    data object Infinite: BingoWinCondition
    @Serializable
    @SerialName("replace_goals")
    data object ReplaceGoals: BingoWinCondition
}