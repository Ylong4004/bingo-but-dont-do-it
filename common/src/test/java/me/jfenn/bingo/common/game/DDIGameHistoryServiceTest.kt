package me.jfenn.bingo.common.game

import assertk.assertThat
import assertk.assertions.isEqualTo
import me.jfenn.bingo.common.team.BingoTeamKey
import org.junit.jupiter.api.Test

class DDIGameHistoryServiceTest {

    @Test
    fun `snapshot includes undamaged teams and ordered real damage entries`() {
        val service = DDIGameHistoryService()
        val red = BingoTeamKey("bingo_red")
        val blue = BingoTeamKey("bingo_blue")
        service.registerTeam(red, "红")
        service.registerTeam(blue, "蓝")

        service.recordDamage(red, "红", "不要跳跃", "Alice", 2, 3)
        service.recordDamage(red, "红", "立即扣一心", null, 1, 3)

        assertThat(service.snapshot()).isEqualTo(
            listOf(
                DDITeamDamageHistory(
                    teamKey = red,
                    teamName = "红",
                    entries = listOf(
                        DDIDamageHistoryEntry("不要跳跃", "Alice", 2, 3),
                        DDIDamageHistoryEntry("立即扣一心", null, 1, 3),
                    ),
                ),
                DDITeamDamageHistory(
                    teamKey = blue,
                    teamName = "蓝",
                    entries = emptyList(),
                ),
            )
        )
    }

    @Test
    fun `reset removes the previous round`() {
        val service = DDIGameHistoryService()
        service.recordDamage(BingoTeamKey("bingo_red"), "红", "不要跳跃", "Alice", 2, 3)

        service.reset()

        assertThat(service.snapshot()).isEqualTo(emptyList())
    }
}
