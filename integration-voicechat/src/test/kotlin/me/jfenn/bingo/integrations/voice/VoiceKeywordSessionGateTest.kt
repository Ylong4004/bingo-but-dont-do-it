package me.jfenn.bingo.integrations.voice

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

class VoiceKeywordSessionGateTest {
    private val playerId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val gameId = UUID.fromString("00000000-0000-0000-0000-000000000002")

    @Test
    fun `stale revision is rejected before callback and current revision is delivered`() {
        var now = 10_000_000_000L
        val gate = VoiceKeywordSessionGate(nanoTime = { now })
        val target = target(revision = 4L)
        val targets = AtomicReference(mapOf(playerId to target))
        val delivered = mutableListOf<String>()
        gate.activate(VoiceKeywordSession(targets) { delivered += it.matchedSubjectId })

        assertThat(gate.deliver(detection(target.copy(revision = 3L)))).isFalse()
        assertThat(gate.deliver(detection(target))).isTrue()
        assertThat(delivered).containsExactly("voice:等一下")

        now += 1_000_000_000L
        assertThat(gate.deliver(detection(target))).isFalse()

        // 新分配的语音词不能继承上一词条的冷却，即便 revision 在两秒内发生变化。
        val nextTarget = target.copy(revision = 5L, wordId = "voice_hurry")
        targets.set(mapOf(playerId to nextTarget))
        assertThat(gate.deliver(detection(nextTarget))).isTrue()

        now += 2_100_000_000L
        assertThat(gate.deliver(detection(nextTarget))).isTrue()
    }

    @Test
    fun `closing an obsolete lease cannot deactivate replacement session`() {
        val gate = VoiceKeywordSessionGate()
        val firstId = gate.activate(
            VoiceKeywordSession(AtomicReference(mapOf(playerId to target(1L)))) { }
        )
        val secondTarget = target(2L)
        gate.activate(
            VoiceKeywordSession(AtomicReference(mapOf(playerId to secondTarget))) { }
        )

        assertThat(gate.deactivate(firstId)).isFalse()
        assertThat(gate.deliver(detection(secondTarget))).isTrue()
    }

    private fun target(revision: Long) = VoiceKeywordTarget(
        gameId = gameId,
        objectiveId = "team:red",
        revision = revision,
        wordId = "voice_wait",
        subjectIds = setOf("voice:等一下"),
    )

    private fun detection(target: VoiceKeywordTarget) = VoiceKeywordDetection(
        playerId = playerId,
        target = target,
        matchedSubjectId = "voice:等一下",
        confidence = 0.9,
    )
}
