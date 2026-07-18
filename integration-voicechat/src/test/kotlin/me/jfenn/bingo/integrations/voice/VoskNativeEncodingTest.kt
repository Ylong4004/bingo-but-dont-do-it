package me.jfenn.bingo.integrations.voice

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

class VoskNativeEncodingTest {
    @Test
    fun `vosk native strings always use utf8`() {
        val property = "jna.encoding"
        val previous = System.getProperty(property)
        try {
            System.setProperty(property, "GBK")

            VoskNativeEncoding.initialize()

            assertThat(VoskNativeEncoding.current()).isEqualTo("UTF-8")
            assertThat(VoskNativeEncoding.isConfigured()).isEqualTo(true)
            assertThat(System.getProperty(property)).isEqualTo("GBK")
        } finally {
            if (previous == null) {
                System.clearProperty(property)
            } else {
                System.setProperty(property, previous)
            }
        }
    }
}
