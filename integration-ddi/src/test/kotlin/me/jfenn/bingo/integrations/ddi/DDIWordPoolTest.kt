package me.jfenn.bingo.integrations.ddi

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNull
import me.jfenn.bingo.common.team.BingoTeamKey
import org.junit.jupiter.api.Test

class DDIWordPoolTest {

    private val pool = DDIWordPool()

    @Test
    fun `all 171 entries have unique ids and every trigger has a word`() {
        val words = pool.getAllWords()

        assertThat(words).hasSize(171)
        assertThat(words.map { it.id }.toSet()).hasSize(171)
        assertThat(words.map { it.triggerType }.toSet())
            .isEqualTo(DDITriggerType.entries.toSet())
    }

    @Test
    fun `replacement never immediately repeats the previous trigger`() {
        val previous = pool.getAllWords().first()

        repeat(100) {
            assertThat(pool.drawReplacement(previous).triggerType)
                .isNotEqualTo(previous.triggerType)
        }
    }

    @Test
    fun `hard team history is never relaxed`() {
        val chat = pool.getAllWords().first { it.triggerType == DDITriggerType.CHAT }

        repeat(100) {
            val drawn = pool.drawAvailable(
                previous = null,
                triggeredRepeatKeys = setOf(chat.repeatKey),
                softRepeatKeys = pool.getAllWords().map { it.repeatKey }.toSet(),
            )
            assertThat(drawn?.repeatKey).isNotEqualTo(chat.repeatKey)
        }

        assertThat(
            pool.drawAvailable(
                previous = null,
                triggeredRepeatKeys = pool.getAllWords().map { it.repeatKey }.toSet(),
                softRepeatKeys = emptySet(),
            )
        ).isNull()
    }

    @Test
    fun `triggered repeat keys are shared within a team but isolated between teams`() {
        val history = DDITeamWordHistory()
        val orange = BingoTeamKey("bingo_orange")
        val blue = BingoTeamKey("bingo_blue")
        val word = pool.getAllWords().first()

        history.record(orange, word)

        assertThat(word.repeatKey in history.get(orange)).isEqualTo(true)
        assertThat(word.repeatKey in history.get(blue)).isEqualTo(false)
        history.reset()
        assertThat(history.get(orange).isEmpty()).isEqualTo(true)
    }
}
