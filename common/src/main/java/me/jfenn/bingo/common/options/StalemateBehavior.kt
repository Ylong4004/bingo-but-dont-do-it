package me.jfenn.bingo.common.options

import kotlinx.serialization.Serializable
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.generated.StringKey

@Serializable
enum class StalemateBehavior(
    val string: StringKey,
) {
    END_GAME(StringKey.OptionsWinBehaviorStalemateEndGame),
    REROLL_CARD(StringKey.OptionsWinBehaviorStalemateRerollCard),
    NOTHING(StringKey.OptionsWinBehaviorStalemateDoNothing);

    fun string(text: TextProvider) =
        text.string(StringKey.OptionsWinBehaviorStalemate, string)
}
