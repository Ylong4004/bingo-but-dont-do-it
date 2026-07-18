package me.jfenn.bingo.integrations.ddi

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import me.jfenn.bingo.common.team.BingoTeamKey
import net.minecraft.util.Formatting
import org.junit.jupiter.api.Test
import java.util.UUID

class DDITabLivesProjectionTest {

    @Test
    fun `团队共享模式为同队成员显示同一生命`() {
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        val objective = objective("team:red", setOf(first, second), hearts = 2, shared = true)
        val assignments = mapOf(first to objective.objectiveId, second to objective.objectiveId)
        val objectives = mapOf(objective.objectiveId to objective)

        assertThat(resolve(first, assignments, objectives)).isEqualTo(2)
        assertThat(resolve(second, assignments, objectives)).isEqualTo(2)

        objective.hearts = 1
        assertThat(resolve(first, assignments, objectives)).isEqualTo(1)
        assertThat(resolve(second, assignments, objectives)).isEqualTo(1)
    }

    @Test
    fun `个人模式分别显示各自生命且结束后不再显示`() {
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        val outsider = UUID.randomUUID()
        val firstObjective = objective("player:first", setOf(first), hearts = 3, shared = false)
        val secondObjective = objective("player:second", setOf(second), hearts = 1, shared = false)
        val assignments = mapOf(
            first to firstObjective.objectiveId,
            second to secondObjective.objectiveId,
        )
        val objectives = mapOf(
            firstObjective.objectiveId to firstObjective,
            secondObjective.objectiveId to secondObjective,
        )

        assertThat(resolve(first, assignments, objectives)).isEqualTo(3)
        assertThat(resolve(second, assignments, objectives)).isEqualTo(1)
        assertThat(resolve(outsider, assignments, objectives)).isNull()
        assertThat(resolve(first, assignments, objectives, roundActive = false)).isNull()
    }

    private fun resolve(
        playerId: UUID,
        assignments: Map<UUID, String>,
        objectives: Map<String, DDIObjectiveState>,
        roundActive: Boolean = true,
    ) = DDITabLivesProjection.resolve(
        playerId = playerId,
        roundActive = roundActive,
        inactivePlayerIds = emptySet(),
        playerObjectiveIds = assignments,
        objectiveStates = objectives,
    )

    private fun objective(
        id: String,
        members: Set<UUID>,
        hearts: Int,
        shared: Boolean,
    ) = DDIObjectiveState(
        objectiveId = id,
        objectiveName = id,
        teamKey = BingoTeamKey("bingo_red"),
        teamName = "红队",
        teamColor = Formatting.RED,
        memberIds = members,
        memberNames = emptyList(),
        isTeamShared = shared,
        hearts = hearts,
        maxHearts = 3,
    )
}
