package me.jfenn.bingo.integrations.ddi

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
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
}
