package me.jfenn.bingo.api

import me.jfenn.bingo.api.data.BingoTeamScore
import me.jfenn.bingo.api.data.IBingoTeam
import me.jfenn.bingo.common.team.BingoTeam
import java.util.*

class BingoTeamImpl(
    val team: BingoTeam,
) : IBingoTeam {
    override val id: String
        get() = team.id
    override val players: List<UUID>
        get() = team.players.map { it.uuid }
    override val score: BingoTeamScore
        get() = BingoTeamScore(
            items = team.score.items,
            lines = team.score.lines,
            cards = team.score.cards,
        )
}
