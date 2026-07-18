package me.jfenn.bingo.integrations.ddi

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.mockk.every
import io.mockk.mockk
import me.jfenn.bingo.common.config.PlayerSettingsService
import me.jfenn.bingo.common.game.DDIGameHistoryService
import me.jfenn.bingo.common.options.BingoOptions
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.state.GameState
import org.junit.jupiter.api.Test
import org.slf4j.Logger

class DDIDebugWordConfigurationTest {
    @Test
    fun lobbyCanConfigureAndClearOneShotFirstWord() {
        val state = mockk<BingoState>(relaxed = true)
        val options = mockk<BingoOptions>(relaxed = true)
        val wordPool = mockk<DDIWordPool>(relaxed = true)
        val word = DDIWordPool.WordEntry(
            id = "voice_diamond",
            displayText = "说出“钻石”",
            triggerType = DDITriggerType.SPEAK_KEYWORD,
        )
        every { state.state } returns GameState.PREGAME
        every { state.options } returns options
        every { options.ddiVoiceCustomKeywords } returns emptyList()
        every { wordPool.findById(word.id) } returns word

        val manager = manager(state, wordPool)
        val configured = manager.configureDebugNextRoundWord(word.id)

        assertThat(configured.success).isEqualTo(true)
        assertThat(manager.debugNextRoundWordId()).isEqualTo(word.id)
        assertThat(manager.clearDebugNextRoundWord().success).isEqualTo(true)
        assertThat(manager.debugNextRoundWordId()).isEqualTo(null)
    }

    @Test
    fun activeGameCannotConfigureNextRoundFirstWord() {
        val state = mockk<BingoState>(relaxed = true)
        every { state.state } returns GameState.PLAYING
        val manager = manager(state, mockk(relaxed = true))

        val result = manager.configureDebugNextRoundWord("voice_diamond")

        assertThat(result.success).isEqualTo(false)
        assertThat(manager.debugNextRoundWordId()).isEqualTo(null)
    }

    private fun manager(
        state: BingoState,
        wordPool: DDIWordPool,
    ) = DDIObjectiveManager(
        state = state,
        wordPool = wordPool,
        triggerDetector = mockk(relaxed = true),
        packets = mockk(relaxed = true),
        tabLivesService = mockk(relaxed = true),
        historyService = mockk<DDIGameHistoryService>(relaxed = true),
        playerSettingsService = mockk<PlayerSettingsService>(relaxed = true),
        log = mockk<Logger>(relaxed = true),
    )
}
