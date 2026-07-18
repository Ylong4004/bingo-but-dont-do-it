package me.jfenn.bingo.integrations.ddi

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import me.jfenn.bingo.common.options.DDISpecialEventType
import me.jfenn.bingo.integrations.ddi.special.DDISpecialEventCatalog
import me.jfenn.bingo.integrations.ddi.special.DDISpecialEventSelector
import org.junit.jupiter.api.Test
import kotlin.random.Random

class DDISpecialEventSelectorTest {

    @Test
    fun `catalog covers every stable event id`() {
        assertThat(DDISpecialEventType.entries).hasSize(30)
        assertThat(DDISpecialEventCatalog.definitions.keys)
            .isEqualTo(DDISpecialEventType.entries.toSet())
    }

    @Test
    fun `last three events are excluded whenever four choices exist`() {
        val choices = DDISpecialEventType.entries.take(4).toSet()
        val selector = DDISpecialEventSelector(ZeroRandom, recentLimit = 3)

        val draws = List(5) { selector.draw(choices) }

        assertThat(draws).containsExactly(
            DDISpecialEventType.entries[0],
            DDISpecialEventType.entries[1],
            DDISpecialEventType.entries[2],
            DDISpecialEventType.entries[3],
            DDISpecialEventType.entries[0],
        )
    }

    @Test
    fun `empty and singleton pools remain safe`() {
        val selector = DDISpecialEventSelector(ZeroRandom)
        assertThat(selector.draw(emptySet())).isEqualTo(null)
        assertThat(selector.draw(setOf(DDISpecialEventType.CALM)))
            .isEqualTo(DDISpecialEventType.CALM)
        assertThat(selector.draw(setOf(DDISpecialEventType.CALM)))
            .isEqualTo(DDISpecialEventType.CALM)
    }

    private object ZeroRandom : Random() {
        override fun nextBits(bitCount: Int): Int = 0
    }
}
