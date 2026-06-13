package me.jfenn.bingo.common.event.model

import me.jfenn.bingo.common.team.BingoTeam
import me.jfenn.bingo.platform.event.IEvent

data class TeamWinnerEvent(
    val team: BingoTeam,
) {
    companion object : IEvent<TeamWinnerEvent>
}