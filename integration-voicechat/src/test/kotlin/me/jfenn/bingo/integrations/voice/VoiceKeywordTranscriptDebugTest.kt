package me.jfenn.bingo.integrations.voice

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

class VoiceKeywordTranscriptDebugTest {
    @Test
    fun `debug transcripts stay opt-in bounded and expire`() {
        val now = AtomicLong(0L)
        val buffer = VoiceKeywordTranscriptBuffer(
            nowNanos = now::get,
            sessionDurationNanos = 10L,
            capacity = 2,
        )
        val player = UUID.randomUUID()

        buffer.capture(player, """{"text":"不应记录"}""", VoiceKeywordResultEvaluation.TextMismatch)
        assertThat(buffer.snapshot(player).enabled).isFalse()

        buffer.enable(player)
        buffer.capture(
            player,
            """{"result":[{"word":"跳"},{"word":"一下"}],"text":"跳 一下"}""",
            VoiceKeywordResultEvaluation.LowConfidence(average = 0.47, minimum = 0.22),
        )
        buffer.capture(
            player,
            """{"text":"[unk]"}""",
            VoiceKeywordResultEvaluation.TextMismatch,
        )
        buffer.capture(
            player,
            """{"text":"跳一下"}""",
            VoiceKeywordResultEvaluation.Matched(
                VoiceKeywordMatch("voice:跳一下", 0.86),
            ),
        )

        val snapshot = buffer.snapshot(player)
        assertThat(snapshot.enabled).isTrue()
        assertThat(snapshot.entries.map { it.transcript }).isEqualTo(listOf("[unk]", "跳一下"))
        assertThat(snapshot.entries.last().outcome).isEqualTo(VoiceKeywordTranscriptOutcome.MATCHED)

        now.set(10L)
        assertThat(buffer.snapshot(player).enabled).isFalse()
        assertThat(buffer.snapshot(player).entries).isEqualTo(emptyList())
    }
}
