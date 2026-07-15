package me.jfenn.bingo.integrations.ddi

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test
import java.util.UUID

class DDITravelStatSamplerTest {

    private val playerId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @Test
    fun `first sample establishes a baseline and later samples return positive deltas`() {
        val sampler = DDITravelStatSampler()

        assertThat(
            sampler.sample(playerId, DDISignalKind.DISTANCE_WALKED_CM, 12_000)
        ).isEqualTo(0)
        assertThat(
            sampler.sample(playerId, DDISignalKind.DISTANCE_WALKED_CM, 12_037)
        ).isEqualTo(37)
        assertThat(
            sampler.sample(playerId, DDISignalKind.DISTANCE_WALKED_CM, 12_037)
        ).isEqualTo(0)
    }

    @Test
    fun `statistic regressions and signal changes rebaseline instead of adding progress`() {
        val sampler = DDITravelStatSampler()

        sampler.sample(playerId, DDISignalKind.DISTANCE_WALKED_CM, 1_000)
        assertThat(
            sampler.sample(playerId, DDISignalKind.DISTANCE_WALKED_CM, 900)
        ).isEqualTo(0)
        assertThat(
            sampler.sample(playerId, DDISignalKind.DISTANCE_WALKED_CM, 925)
        ).isEqualTo(25)

        assertThat(
            sampler.sample(playerId, DDISignalKind.DISTANCE_SPRINTED_CM, 5_000)
        ).isEqualTo(0)
        assertThat(
            sampler.sample(playerId, DDISignalKind.DISTANCE_SPRINTED_CM, 5_080)
        ).isEqualTo(80)
    }

    @Test
    fun `word reset prevents lifetime statistics leaking into the new objective`() {
        val sampler = DDITravelStatSampler()

        sampler.sample(playerId, DDISignalKind.DISTANCE_BOAT_CM, 20_000)
        assertThat(
            sampler.sample(playerId, DDISignalKind.DISTANCE_BOAT_CM, 20_250)
        ).isEqualTo(250)

        sampler.reset(playerId)
        assertThat(
            sampler.sample(playerId, DDISignalKind.DISTANCE_BOAT_CM, 25_000)
        ).isEqualTo(0)
    }
}
