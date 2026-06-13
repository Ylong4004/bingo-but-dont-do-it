package me.jfenn.bingo.common.scoring

import io.mockk.every
import io.mockk.mockk
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.team.BingoTeam
import me.jfenn.bingo.common.team.BingoTeamKey
import me.jfenn.bingo.common.team.TeamScore
import me.jfenn.bingo.common.team.TeamWinner
import java.time.Duration
import java.time.Instant

fun mockTeam(
    name: String,
    score: TeamScore,
    winner: TeamWinner? = null,
) = mockk<BingoTeam>().also {
    every { it.id } returns name
    every { it.key } returns BingoTeamKey(name)
    every { it.countCards() } returns score.cards
    every { it.score } returns score
    every { it.winner } returns winner
}

fun mockState(
    teams: List<BingoTeam>,
    startedAt: Instant = Instant.ofEpochSecond(1746464705),
) = mockk<BingoState>().also {
    every { it.getRegisteredTeams() } returns teams
    every { it.cards } returns mutableListOf()
    every { it.startedAt } returns startedAt
    every { it.timeAdjustment } returns Duration.ZERO
    every { it.timeOffline } returns Duration.ZERO
}
