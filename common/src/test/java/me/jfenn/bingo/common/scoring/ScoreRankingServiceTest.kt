package me.jfenn.bingo.common.scoring

import assertk.assertThat
import assertk.assertions.isEqualTo
import me.jfenn.bingo.common.team.BingoTeamKey
import me.jfenn.bingo.common.team.TeamScore
import me.jfenn.bingo.common.team.TeamWinner
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class ScoreRankingServiceTest {

    @Test
    fun `given three teams with one complete, ranks teams by score`() {
        val state = mockState(
            teams = listOf(
                mockTeam("blue", TeamScore(18, 3, 0)),
                mockTeam("green", TeamScore(20, 4, 0)),
                mockTeam(
                    name = "yellow",
                    score = TeamScore(25, 5, 0),
                    winner = TeamWinner(Instant.ofEpochSecond(1746468305)),
                ),
            )
        )

        val rankings = ScoreRankingService.getScoreRankings(state)

        assertThat(rankings).isEqualTo(
            listOf(
                ScoreRanking(
                    index = 0,
                    key = BingoTeamKey("yellow"),
                    score = TeamScore(25, 5, 0),
                    duration = Duration.ofHours(1L),
                ),
                ScoreRanking(
                    index = 1,
                    key = BingoTeamKey("green"),
                    score = TeamScore(20, 4, 0),
                    duration = null,
                ),
                ScoreRanking(
                    index = 2,
                    key = BingoTeamKey("blue"),
                    score = TeamScore(18, 3, 0),
                    duration = null,
                )
            )
        )
    }

    @Test
    fun `if two of three teams are tied, they should have the same index`() {
        val state = mockState(
            teams = listOf(
                mockTeam("blue", TeamScore(18, 3, 0)),
                mockTeam("green", TeamScore(20, 4, 0)),
                mockTeam("yellow", TeamScore(18, 3, 0)),
            )
        )

        val rankings = ScoreRankingService.getScoreRankings(state)

        assertThat(rankings).isEqualTo(
            listOf(
                ScoreRanking(
                    index = 0,
                    key = BingoTeamKey("green"),
                    score = TeamScore(20, 4, 0),
                    duration = null,
                ),
                ScoreRanking(
                    index = 1,
                    key = BingoTeamKey("blue"),
                    score = TeamScore(18, 3, 0),
                    duration = null,
                ),
                ScoreRanking(
                    index = 1,
                    key = BingoTeamKey("yellow"),
                    score = TeamScore(18, 3, 0),
                    duration = null,
                )
            )
        )
    }

    @Test
    fun `if multiple teams are winners, they should be ordered by completion time`() {
        val state = mockState(
            teams = listOf(
                mockTeam("blue", TeamScore(18, 3, 0)),
                mockTeam(
                    name = "green",
                    score = TeamScore(25, 5, 0),
                    winner = TeamWinner(Instant.ofEpochSecond(1746471905)),
                ),
                mockTeam(
                    name = "yellow",
                    score = TeamScore(25, 5, 0),
                    winner = TeamWinner(Instant.ofEpochSecond(1746468305)),
                ),
            )
        )

        val rankings = ScoreRankingService.getScoreRankings(state)

        assertThat(rankings).isEqualTo(
            listOf(
                ScoreRanking(
                    index = 0,
                    key = BingoTeamKey("yellow"),
                    score = TeamScore(25, 5, 0),
                    duration = Duration.ofHours(1L),
                ),
                ScoreRanking(
                    index = 1,
                    key = BingoTeamKey("green"),
                    score = TeamScore(25, 5, 0),
                    duration = Duration.ofHours(2L),
                ),
                ScoreRanking(
                    index = 2,
                    key = BingoTeamKey("blue"),
                    score = TeamScore(18, 3, 0),
                    duration = null,
                )
            )
        )
    }
}