package me.jfenn.bingo.common.options

import kotlinx.serialization.Serializable
import me.jfenn.bingo.platform.text.IText
import me.jfenn.bingo.common.card.filter.ObjectiveFilterList
import me.jfenn.bingo.common.card.tierlist.TierLabel
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.generated.StringKey

/**
 * Card options are initialized once and passed by reference into [me.jfenn.bingo.common.card.BingoCard]
 * from the list of [BingoOptions.cards]
 *
 * This way, the settings stay in sync and any changes are applied to both files
 * (game-options.json and [me.jfenn.bingo.common.state.BingoState.cards])
 */
@Serializable
data class BingoCardOptions(
    var goal: BingoGoal = BingoGoal.Lines(1),
    var itemDistribution: List<Int> = TierLabel.DIFFICULTY_EASY,
    var itemFilter: ObjectiveFilterList = ObjectiveFilterList.EVERYTHING,

    // game mode settings
    var isLockoutMode: Boolean = false,
    var isInventoryMode: Boolean = false,
    var isHiddenItemsMode: Boolean = false,
    var isConsumeItemsMode: Boolean = false,
) {
    fun isValid(): Boolean {
        return kotlin.runCatching { assertValid() }.isSuccess
    }

    fun assertValid() {
        assert(itemDistribution.size == TierLabel.entries.size) { "Item distribution $itemDistribution does not match the number of tiers (${TierLabel.entries.size})!" }
    }

    fun formatGameMode(): List<StringKey> {
        val gameMode = buildList {
            if (isLockoutMode) add(StringKey.OptionsModeLockout)
            if (isInventoryMode) add(StringKey.OptionsModeInventory)
            if (isHiddenItemsMode) add(StringKey.OptionsModeHiddenItems)
            if (isConsumeItemsMode) add(StringKey.OptionsModeConsumeItems)
        }

        return gameMode.ifEmpty { listOf(StringKey.OptionsModeStandard) }
    }

    fun formatItemDist(text: TextProvider): IText {
        return TierLabel.entries
            .mapIndexed { i, label ->
                text.literal("${itemDistribution[i]}").formatted(label.formatting)
            }
            .let { text.joinText(it, text.literal(" ")) }
    }
}