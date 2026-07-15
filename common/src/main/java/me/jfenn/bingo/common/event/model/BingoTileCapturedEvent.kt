package me.jfenn.bingo.common.event.model

import me.jfenn.bingo.common.team.BingoTeamKey
import me.jfenn.bingo.platform.event.IEvent
import java.util.UUID

/**
 * Emitted after Bingo has authoritatively accepted a tile capture.
 *
 * Coordinates are zero-based to match [me.jfenn.bingo.common.card.BingoCard.entry].
 * [playerId] is null when the objective cannot be attributed to one player.
 */
data class BingoTileCapturedEvent(
    val gameId: UUID,
    val cardId: UUID,
    val team: BingoTeamKey,
    val playerId: UUID?,
    val objectiveId: String,
    val x: Int,
    val y: Int,
) {
    companion object : IEvent<BingoTileCapturedEvent>
}
