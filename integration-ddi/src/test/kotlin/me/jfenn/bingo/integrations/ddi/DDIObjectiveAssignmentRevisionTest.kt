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

        val slot = objective.slots.single()
        slot.assignWord(word, 60)
        assertThat(slot.assignmentRevision).isEqualTo(1)
        slot.clearWord()
        assertThat(slot.assignmentRevision).isEqualTo(2)
        slot.assignWord(word, 60)
        assertThat(slot.assignmentRevision).isEqualTo(3)
    }

    @Test
    fun `word slots keep asynchronous revisions independent`() {
        val objective = DDIObjectiveState(
            objectiveId = "team:red",
            objectiveName = "红队",
            teamKey = BingoTeamKey("bingo_red"),
            teamName = "红队",
            teamColor = Formatting.RED,
            memberIds = setOf(UUID.randomUUID()),
            memberNames = listOf("player"),
            isTeamShared = true,
            slots = mutableListOf(DDIWordSlotState(0), DDIWordSlotState(1)),
        )
        val word = DDIWordPool.WordEntry(
            id = "test",
            displayText = "测试",
            triggerType = DDITriggerType.SNEAK,
        )

        objective.slot(0)!!.assignWord(word, 60)
        objective.slot(1)!!.assignWord(word, 60)
        objective.slot(0)!!.clearWord()

        assertThat(objective.slot(0)!!.assignmentRevision).isEqualTo(2)
        assertThat(objective.slot(1)!!.assignmentRevision).isEqualTo(1)
        assertThat(objective.slot(1)!!.currentWord).isEqualTo(word)
    }
}
