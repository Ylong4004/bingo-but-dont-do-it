package me.jfenn.bingo.integrations.voice

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.UUID

class VoiceKeywordDiagnosticsTest {
    @AfterEach
    fun clearDiagnostics() {
        VoiceKeywordDiagnostics.reset()
    }

    @Test
    fun perPlayerCountersRemainSeparateWhileAggregateIncludesBothPlayers() {
        VoiceKeywordDiagnostics.reset()
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()

        VoiceKeywordDiagnostics.record(first, VoiceKeywordDiagnosticStage.MICROPHONE_PACKET)
        VoiceKeywordDiagnostics.record(first, VoiceKeywordDiagnosticStage.TARGETED_PACKET)
        VoiceKeywordDiagnostics.record(
            first,
            VoiceKeywordDiagnosticStage.AUDIO_DECODED,
            samples = 320,
        )
        VoiceKeywordDiagnostics.record(second, VoiceKeywordDiagnosticStage.MICROPHONE_PACKET)
        VoiceKeywordDiagnostics.record(second, VoiceKeywordDiagnosticStage.NO_ACTIVE_TARGET)

        val aggregate = VoiceKeywordDiagnostics.snapshot(
            playerId = null,
            connectedPlayers = 2,
            activeTargets = 1,
        )
        val firstSnapshot = VoiceKeywordDiagnostics.snapshot(
            playerId = first,
            connectedPlayers = 2,
            activeTargets = 1,
        )
        val secondSnapshot = VoiceKeywordDiagnostics.snapshot(
            playerId = second,
            connectedPlayers = 2,
            activeTargets = 1,
        )

        assertThat(aggregate.microphonePackets).isEqualTo(2L)
        assertThat(aggregate.decodedSamples).isEqualTo(320L)
        assertThat(firstSnapshot.targetedPackets).isEqualTo(1L)
        assertThat(firstSnapshot.noTargetPackets).isEqualTo(0L)
        assertThat(secondSnapshot.targetedPackets).isEqualTo(0L)
        assertThat(secondSnapshot.noTargetPackets).isEqualTo(1L)
    }

    @Test
    fun resetRemovesPreviousPlayerCounters() {
        val playerId = UUID.randomUUID()
        VoiceKeywordDiagnostics.record(
            playerId,
            VoiceKeywordDiagnosticStage.RESULT_LOW_CONFIDENCE,
        )

        VoiceKeywordDiagnostics.reset()
        val snapshot = VoiceKeywordDiagnostics.snapshot(
            playerId = playerId,
            connectedPlayers = 0,
            activeTargets = 0,
        )

        assertThat(snapshot.lowConfidenceResults).isEqualTo(0L)
        assertThat(snapshot.lastStage).isEqualTo(null)
    }
}
