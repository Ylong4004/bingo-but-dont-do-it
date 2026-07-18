package me.jfenn.bingo.common.event.model

import me.jfenn.bingo.common.team.BingoTeamKey
import me.jfenn.bingo.platform.event.IEvent
import java.util.UUID

/**
 * 在 Bingo 权威确认一次格子占领后发送。
 *
 * 坐标从零开始，以匹配 [me.jfenn.bingo.common.card.BingoCard.entry]。
 * 无法把 objective 归属到某一名玩家时，[playerId] 为空。
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
