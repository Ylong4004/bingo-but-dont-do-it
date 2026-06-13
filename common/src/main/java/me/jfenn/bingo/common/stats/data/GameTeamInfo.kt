package me.jfenn.bingo.common.stats.data

import kotlinx.serialization.Serializable
import me.jfenn.bingo.common.team.BingoTeamKey
import me.jfenn.bingo.platform.utils.UuidAsString
import me.jfenn.bingo.stats.sql.GameTeam
import java.util.*

@Serializable
data class GameTeamInfo(
    val id: BingoTeamKey,
    val gameId: UuidAsString,
    val name: String,
    val isWinner: Boolean
) {
    constructor(team: GameTeam) : this(
        id = BingoTeamKey(team.id),
        gameId = UUID.fromString(team.game_id),
        name = team.name,
        isWinner = team.is_winner == 1L,
    )

    fun toGameTeam() = GameTeam(
        id = this.id.id,
        game_id = this.gameId.toString(),
        name = this.name,
        is_winner = if (this.isWinner) 1L else 0L
    )
}