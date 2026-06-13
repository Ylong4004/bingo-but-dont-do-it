package me.jfenn.bingo.common.game

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.jfenn.bingo.platform.text.IText
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.generated.StringKey

@Serializable
sealed interface GameEndReason {
    fun format(text: TextProvider): IText

    @Serializable
    @SerialName("bingo")
    data object Bingo : GameEndReason {
        override fun format(text: TextProvider): IText {
            return text.string(StringKey.GameEndReasonBingo)
        }
    }

    @Serializable
    @SerialName("lockout")
    data object Lockout : GameEndReason {
        override fun format(text: TextProvider): IText {
            return text.string(StringKey.GameEndReasonLockout)
        }
    }

    @Serializable
    @SerialName("stalemate")
    data object Stalemate : GameEndReason {
        override fun format(text: TextProvider): IText {
            return text.string(StringKey.GameEndReasonStalemate)
        }
    }

    @Serializable
    @SerialName("impossible_goal")
    data object ImpossibleGoal : GameEndReason {
        override fun format(text: TextProvider): IText {
            return text.string(StringKey.GameEndReasonImpossibleGoal)
        }
    }

    @Serializable
    @SerialName("timer")
    data object Timer : GameEndReason {
        override fun format(text: TextProvider): IText {
            return text.string(StringKey.GameEndReasonTimer)
        }
    }

    @Serializable
    @SerialName("command")
    class Command(val playerName: String) : GameEndReason {
        override fun format(text: TextProvider): IText {
            return text.string(StringKey.GameEndReasonCommand, playerName)
        }
    }
}