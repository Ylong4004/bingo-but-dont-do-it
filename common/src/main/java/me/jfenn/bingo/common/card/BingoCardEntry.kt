package me.jfenn.bingo.common.card

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import me.jfenn.bingo.common.card.objective.ObjectiveDisplay
import me.jfenn.bingo.common.card.tierlist.TierLabel

@Serializable
class BingoCardEntry(
    val objectiveId: String,
    val tier: TierLabel?,
    /** The tier list ID that this entry was created from */
    val source: String?,
    var tileName: String? = null,
) {
    @Transient
    var display: ObjectiveDisplay.Resolved = ObjectiveDisplay.Resolved.EMPTY
}
