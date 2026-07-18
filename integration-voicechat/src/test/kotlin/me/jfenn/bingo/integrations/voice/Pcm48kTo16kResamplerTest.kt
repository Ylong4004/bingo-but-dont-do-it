package me.jfenn.bingo.integrations.voice

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

class Pcm48kTo16kResamplerTest {
    @Test
    fun `one voice frame produces exactly one third as many samples`() {
        val output = Pcm48kTo16kResampler().process(ShortArray(960))
        assertThat(output.size).isEqualTo(320)
    }

    @Test
    fun `state and output count are preserved across arbitrary chunks`() {
        val resampler = Pcm48kTo16kResampler()
        val sizes = listOf(127, 1, 833, 959)
        val outputSize = sizes.sumOf { resampler.process(ShortArray(it)).size }
        assertThat(outputSize).isEqualTo(sizes.sum() / 3)
    }

    @Test
    fun `low pass keeps voice band energy and attenuates out-of-band aliases`() {
        val low = resampleSine(1_000.0)
        val high = resampleSine(12_000.0)
        val lowRms = rms(low.drop(100))
        val highRms = rms(high.drop(100))

        assertThat(lowRms > 5_000.0).isTrue()
        assertThat(highRms < lowRms * 0.12).isTrue()
    }

    private fun resampleSine(frequency: Double): ShortArray {
        val input = ShortArray(48_000) { index ->
            (sin(2.0 * PI * frequency * index / 48_000.0) * 12_000.0).toInt().toShort()
        }
        return Pcm48kTo16kResampler().process(input)
    }

    private fun rms(samples: List<Short>): Double = sqrt(
        samples.sumOf { it.toDouble() * it.toDouble() } / samples.size
    )
}
