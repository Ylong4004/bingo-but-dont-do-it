package me.jfenn.bingo.integrations.ddi

import assertk.assertThat
import assertk.assertions.isEqualTo
import me.jfenn.bingo.common.team.BingoTeamKey
import net.minecraft.util.Formatting
import org.junit.jupiter.api.Test
import java.util.UUID

class DDIObjectiveAssignmentRevisionTest {

    @Test
    fun `every deal and clear invalidates asynchronous assignment tokens`() {
        val objective = DDIObjectiveState(
            objectiveId = "team:red",
            objectiveName = "红队",
            teamKey = BingoTeamKey("bingo_red"),
            teamName = "红队",
            teamColor = Formatting.RED,
            memberIds = setOf(UUID.randomUUID()),
            memberNames = listOf("player"),
            isTeamShared = true,
        )
        val word = DDIWordPool.WordEntry(
            id = "voice_test",
            displayText = "说出“测试”",
            triggerType = DDITriggerType.SPEAK_KEYWORD,
            category = "voice",
            rule = DDIRuleDefinition(
                signalKind = DDISignalKind.VOICE_KEYWORD_SPOKEN,
                subjectIds = setOf("voice:测试"),
            ),
        )

        objective.assignWord(word, 60)
        assertThat(objective.assignmentRevision).isEqualTo(1)
        objective.clearWord()
        assertThat(objective.assignmentRevision).isEqualTo(2)
        objective.assignWord(word, 60)
        assertThat(objective.assignmentRevision).isEqualTo(3)
    }
}
