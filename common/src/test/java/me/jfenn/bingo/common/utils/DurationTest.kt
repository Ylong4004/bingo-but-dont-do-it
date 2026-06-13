package me.jfenn.bingo.common.utils

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test
import java.time.Duration

class DurationTest {

    @Test
    fun formatString_hours() {
        val duration = 3.hours + 10.minutes + 1.seconds
        assertThat(duration.formatString()).isEqualTo("3h 10m 01s")
    }

    @Test
    fun formatString_seconds() {
        val duration = 1.seconds + 123.milliseconds
        assertThat(duration.formatString()).isEqualTo("00m 01s")
    }

    @Test
    fun formatString_millis() {
        val duration = 123.milliseconds
        assertThat(duration.formatString()).isEqualTo("00m 00s")
    }

    @Test
    fun formatString_negative() {
        val duration = Duration.ZERO - 3.minutes
        assertThat(duration.formatString()).isEqualTo("-03m 00s")
    }

    @Test
    fun formatStringSmall_hours() {
        val duration = 3.hours + 10.minutes + 1.seconds
        assertThat(duration.formatStringSmall()).isEqualTo("3h")
    }

    @Test
    fun formatStringSmall_seconds() {
        val duration = 1.seconds + 123.milliseconds
        assertThat(duration.formatStringSmall()).isEqualTo("1s")
    }

    @Test
    fun formatStringSmall_millis() {
        val duration = 123.milliseconds
        assertThat(duration.formatStringSmall()).isEqualTo("0.123s")
    }

    @Test
    fun formatStringSmall_negative() {
        val duration = Duration.ZERO - 3.minutes
        assertThat(duration.formatStringSmall()).isEqualTo("-3m")
    }

}