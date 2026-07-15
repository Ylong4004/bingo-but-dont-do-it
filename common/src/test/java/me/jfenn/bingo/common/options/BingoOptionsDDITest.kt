package me.jfenn.bingo.common.options

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotEqualTo
import assertk.assertions.isTrue
import kotlinx.serialization.decodeFromString
import me.jfenn.bingo.common.utils.json
import org.junit.jupiter.api.Test

class BingoOptionsDDITest {

    @Test
    fun `old options without an objective mode keep individual behavior`() {
        val options = json.decodeFromString<BingoOptions>("{}")

        assertThat(options.ddiObjectiveMode).isEqualTo(DDIObjectiveMode.INDIVIDUAL)
        assertThat(options.ddiMaxHearts).isEqualTo(3)
    }

    @Test
    fun `DDI objective modes have different difficulty hashes`() {
        val individual = BingoOptions(
            enableDDI = true,
            ddiObjectiveMode = DDIObjectiveMode.INDIVIDUAL,
        )
        val teamShared = individual.copy(ddiObjectiveMode = DDIObjectiveMode.TEAM_SHARED)

        assertThat(teamShared.getShaHash()).isNotEqualTo(individual.getShaHash())
    }

    @Test
    fun `disabled DDI keeps its existing hash independent of objective mode`() {
        val individual = BingoOptions(ddiObjectiveMode = DDIObjectiveMode.INDIVIDUAL)
        val teamShared = individual.copy(ddiObjectiveMode = DDIObjectiveMode.TEAM_SHARED)

        assertThat(teamShared.getShaHash()).isEqualTo(individual.getShaHash())
    }

    @Test
    fun `DDI heart and timer values have unambiguous hash boundaries`() {
        val oneHeart = BingoOptions(
            enableDDI = true,
            ddiMaxHearts = 1,
            ddiWordTimerSeconds = 160,
        )
        val elevenHearts = oneHeart.copy(
            ddiMaxHearts = 11,
            ddiWordTimerSeconds = 60,
        )

        assertThat(oneHeart.getShaHash()).isNotEqualTo(elevenHearts.getShaHash())
    }

    @Test
    fun `enabled DDI rejects out of range persisted values`() {
        assertThat(BingoOptions(enableDDI = true, ddiMaxHearts = 0).isValid()).isFalse()
        assertThat(BingoOptions(enableDDI = true, ddiMaxHearts = 21).isValid()).isFalse()
        assertThat(BingoOptions(enableDDI = true, ddiWordTimerSeconds = 9).isValid()).isFalse()
        assertThat(BingoOptions(enableDDI = true, ddiWordTimerSeconds = 601).isValid()).isFalse()
    }

    @Test
    fun `disabled DDI does not invalidate an old saved configuration`() {
        assertThat(
            BingoOptions(
                enableDDI = false,
                ddiMaxHearts = 0,
                ddiWordTimerSeconds = 0,
            ).isValid()
        ).isTrue()
    }
}
