package me.jfenn.bingo.common.scoring

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import me.jfenn.bingo.common.team.TeamScore
import org.junit.jupiter.api.Test

class ScoreServiceTest {

    @Test
    fun `given three teams, when two are tied by items, then the leading team is null`() {
        val state = mockState(
            teams = listOf(
                mockTeam("pink", TeamScore(0, 0, 0)),
                mockTeam("yellow", TeamScore(5, 0, 0)),
                mockTeam("green", TeamScore(5, 0, 0)),
            )
        )

        val leading = ScoreService.getLeading(state, false)
        assertThat(leading).isNull()
    }

    @Test
    fun `given three teams, when two are tied by items, then the first tied leading team is returned`() {
        val state = mockState(
            teams = listOf(
                mockTeam("pink", TeamScore(0, 0, 0)),
                mockTeam("yellow", TeamScore(5, 0, 0)),
                mockTeam("green", TeamScore(5, 0, 0)),
            )
        )

        val leading = ScoreService.getLeading(state, true)
        assertThat(leading?.id).isEqualTo("yellow")
    }

    @Test
    fun `given three teams, when two are tied by lines, then the leading team is null`() {
        val state = mockState(
            teams = listOf(
                mockTeam("pink", TeamScore(11, 1, 0)),
                mockTeam("yellow", TeamScore(10, 2, 0)),
                mockTeam("green", TeamScore(10, 2, 0)),
            )
        )

        val leading = ScoreService.getLeading(state, false)
        assertThat(leading).isNull()
    }

    @Test
    fun `given three teams, when two are tied by lines, then the first tied leading team is returned`() {
        val state = mockState(
            teams = listOf(
                mockTeam("pink", TeamScore(11, 1, 0)),
                mockTeam("yellow", TeamScore(10, 2, 0)),
                mockTeam("green", TeamScore(10, 2, 0)),
            )
        )

        val leading = ScoreService.getLeading(state, true)
        assertThat(leading?.id).isEqualTo("yellow")
    }
}