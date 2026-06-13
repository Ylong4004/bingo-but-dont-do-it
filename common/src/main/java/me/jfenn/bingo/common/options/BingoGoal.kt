package me.jfenn.bingo.common.options

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.jfenn.bingo.platform.text.IText
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.generated.StringKey

@Serializable
sealed class BingoGoal {
    @Serializable
    @SerialName("items")
    class Items(val items: Int): BingoGoal()
    @Serializable
    @SerialName("lines")
    class Lines(val lines: Int): BingoGoal()

    companion object {
        const val MAX_ITEMS = 25
        const val MAX_LINES = 11
    }

    fun string(): StringKey {
        return when (this) {
            is Items -> StringKey.GoalItems
            is Lines -> StringKey.GoalLines
        }
    }

    fun isFullCard(): Boolean {
        return when (this) {
            is Items -> items >= MAX_ITEMS
            is Lines -> lines >= MAX_LINES
        }
    }

    fun format(textProvider: TextProvider, supportsFullCard: Boolean = true, descriptive: Boolean = false): IText {
        return when {
            supportsFullCard && this.isFullCard() -> textProvider.string(StringKey.GoalFullCard)
                .also {
                    if (descriptive) {
                        it.append(" ")
                        when (this) {
                            is Items -> it.append(textProvider.string(StringKey.GoalItems).bracketed())
                            is Lines -> it.append(textProvider.string(StringKey.GoalLines).bracketed())
                        }
                    }
                }
            else -> when (this) {
                is Items -> textProvider.itemCount(this.items)
                is Lines -> textProvider.lineCount(this.lines)
            }
        }
    }
}