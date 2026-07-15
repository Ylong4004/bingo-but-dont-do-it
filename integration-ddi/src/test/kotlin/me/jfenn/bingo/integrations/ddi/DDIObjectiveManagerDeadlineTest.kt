package me.jfenn.bingo.integrations.ddi

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import me.jfenn.bingo.common.game.DDIGameHistoryService
import me.jfenn.bingo.common.options.DDIObjectiveMode
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.team.BingoTeamKey
import net.minecraft.util.Formatting
import org.junit.jupiter.api.Test
import org.slf4j.Logger

class DDIObjectiveManagerDeadlineTest {

    @Test
    fun `expiry violation deducts a heart records history and deals another word`() {
        val state = mockk<BingoState>(relaxed = true)
        val wordPool = mockk<DDIWordPool>()
        val detector = mockk<DDITriggerDetector>(relaxed = true)
        val packets = mockk<DDIServerPackets>(relaxed = true)
        val tabLives = mockk<DDITabLivesService>(relaxed = true)
        val history = mockk<DDIGameHistoryService>(relaxed = true)
        val log = mockk<Logger>(relaxed = true)
        val manager = DDIObjectiveManager(
            state = state,
            wordPool = wordPool,
            triggerDetector = detector,
            packets = packets,
            tabLivesService = tabLives,
            historyService = history,
            log = log,
        )
        manager.setPrivate(
            "roundConfig",
            DDIRoundConfig.validated(2, 10, DDIObjectiveMode.TEAM_SHARED),
        )

        val expiring = DDIWordPool.WordEntry(
            id = "deadline_test",
            displayText = "倒计时结束测试",
            triggerType = DDITriggerType.CHAT,
            repeatKey = "deadline:test",
            rule = DDIRuleDefinition(
                signalKind = DDISignalKind.LEGACY,
                legacyTrigger = DDITriggerType.CHAT,
                deadlineBehavior = DDIDeadlineBehavior.TRIGGER_ON_EXPIRY,
            ),
        )
        val replacement = DDIWordPool.WordEntry(
            id = "replacement_test",
            displayText = "替换词",
            triggerType = DDITriggerType.SNEAK,
        )
        val red = objective(
            id = "team:red",
            team = BingoTeamKey("bingo_red"),
            word = expiring,
            hearts = 2,
            timer = 0,
        )
        manager.objectiveStates[red.objectiveId] = red

        val hardHistory = slot<Set<String>>()
        every {
            wordPool.drawAvailable(
                previous = expiring,
                triggeredRepeatKeys = capture(hardHistory),
                softRepeatKeys = any(),
                predicate = any(),
            )
        } returns replacement
        every { wordPool.size() } returns 271

        manager.handleExpiredObjective(red)

        assertThat(red.hearts).isEqualTo(1)
        assertThat(red.currentWord).isEqualTo(replacement)
        assertThat(red.ruleProgress).isEqualTo(0)
        assertThat(expiring.repeatKey in hardHistory.captured).isEqualTo(true)
        verify(exactly = 1) {
            history.recordDamage(
                teamKey = red.teamKey,
                teamName = any(),
                wordText = expiring.displayText,
                actorName = null,
                heartsRemaining = 1,
                maxHearts = 2,
            )
        }
    }

    @Test
    fun `satisfied deadline deals another word without deducting a heart`() {
        val state = mockk<BingoState>(relaxed = true)
        val wordPool = mockk<DDIWordPool>()
        val detector = mockk<DDITriggerDetector>(relaxed = true)
        val packets = mockk<DDIServerPackets>(relaxed = true)
        val tabLives = mockk<DDITabLivesService>(relaxed = true)
        val history = mockk<DDIGameHistoryService>(relaxed = true)
        val manager = DDIObjectiveManager(
            state = state,
            wordPool = wordPool,
            triggerDetector = detector,
            packets = packets,
            tabLivesService = tabLives,
            historyService = history,
            log = mockk(relaxed = true),
        )
        manager.setPrivate(
            "roundConfig",
            DDIRoundConfig.validated(3, 10, DDIObjectiveMode.TEAM_SHARED),
        )
        val deadline = DDIWordPool.WordEntry(
            id = "deadline_safe_test",
            displayText = "倒计时前完成任意格",
            triggerType = DDITriggerType.COMPLETE_BINGO_TILE,
            rule = DDIRuleDefinition(
                signalKind = DDISignalKind.BINGO_TILE_CAPTURED,
                subjectTags = setOf("bingo:any_tile"),
                deadlineBehavior = DDIDeadlineBehavior.TRIGGER_ON_EXPIRY,
                matchBehavior = DDIMatchBehavior.SATISFY_DEADLINE,
            ),
        )
        val replacement = DDIWordPool.WordEntry(
            id = "replacement_safe_test",
            displayText = "替换词",
            triggerType = DDITriggerType.SNEAK,
        )
        val objective = objective(
            id = "team:orange",
            team = BingoTeamKey("bingo_orange"),
            word = deadline,
            hearts = 3,
            timer = 0,
        ).apply { deadlineSatisfied = true }
        manager.objectiveStates[objective.objectiveId] = objective
        every {
            wordPool.drawAvailable(
                previous = deadline,
                triggeredRepeatKeys = any(),
                softRepeatKeys = any(),
                predicate = any(),
            )
        } returns replacement
        every { wordPool.size() } returns 316

        manager.handleExpiredObjective(objective)

        assertThat(objective.hearts).isEqualTo(3)
        assertThat(objective.currentWord).isEqualTo(replacement)
        assertThat(objective.deadlineSatisfied).isEqualTo(false)
        verify(exactly = 0) { history.recordDamage(any(), any(), any(), any(), any(), any()) }
    }

    private fun objective(
        id: String,
        team: BingoTeamKey,
        word: DDIWordPool.WordEntry,
        hearts: Int,
        timer: Int,
    ) = DDIObjectiveState(
        objectiveId = id,
        objectiveName = team.label,
        teamKey = team,
        teamName = team.label,
        teamColor = Formatting.WHITE,
        memberIds = emptySet(),
        memberNames = emptyList(),
        isTeamShared = true,
        currentWord = word,
        hearts = hearts,
        maxHearts = hearts,
        wordTimerSeconds = timer,
        maxWordTimerSeconds = timer,
    )

    private fun Any.setPrivate(name: String, value: Any?) {
        javaClass.getDeclaredField(name).apply {
            isAccessible = true
            set(this@setPrivate, value)
        }
    }
}
