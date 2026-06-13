package me.jfenn.bingo.common.event.model

import me.jfenn.bingo.common.team.BingoTeamKey
import me.jfenn.bingo.common.team.TeamScore
import me.jfenn.bingo.platform.event.IEvent

data class ScoreChangedEvent(
    val team: BingoTeamKey,
    val prevScore: TeamScore,
    val newScore: TeamScore,
) {
    companion object : IEvent<ScoreChangedEvent>
}
