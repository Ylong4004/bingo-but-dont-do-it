package me.jfenn.bingo.integrations.voice

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * 语音关键词流水线的隐私安全阶段。阶段只描述处理位置，
 * 不保存音频、识别文本或自定义关键词。
 */
enum class VoiceKeywordDiagnosticStage {
    MICROPHONE_PACKET,
    NO_ACTIVE_TARGET,
    TARGETED_PACKET,
    MODEL_NOT_READY,
    BACKEND_NOT_READY,
    PACKET_QUEUED,
    QUEUE_DROPPED,
    RESOURCES_READY,
    AUDIO_DECODED,
    SEGMENT_FINALIZED,
    RESULT_EMPTY,
    RESULT_INVALID,
    RESULT_TEXT_MISMATCH,
    RESULT_LOW_CONFIDENCE,
    RESULT_MATCHED,
    RESULT_MATCHED_TEXT_FALLBACK,
    DETECTION_DELIVERED,
    DETECTION_REJECTED,
    DDI_SETTLED,
    DDI_REJECTED,
    PIPELINE_ERROR,
}

/** 可由局内管理员命令安全显示的累计诊断快照。 */
data class VoiceKeywordDiagnosticsSnapshot(
    val playerId: UUID?,
    val sinceEpochMillis: Long,
    val connectedPlayers: Int,
    val activeTargets: Int,
    val microphonePackets: Long,
    val noTargetPackets: Long,
    val targetedPackets: Long,
    val modelNotReadyPackets: Long,
    val backendNotReadyPackets: Long,
    val queuedPackets: Long,
    val droppedPackets: Long,
    val resourceInitializations: Long,
    val decodedFrames: Long,
    val decodedSamples: Long,
    val finalizedSegments: Long,
    val emptyResults: Long,
    val invalidResults: Long,
    val textMismatches: Long,
    val lowConfidenceResults: Long,
    val lastAverageConfidence: Double?,
    val lastMinimumWordConfidence: Double?,
    val matchedResults: Long,
    val textFallbackMatches: Long,
    val deliveredDetections: Long,
    val rejectedDetections: Long,
    val settledDetections: Long,
    val rejectedSettlements: Long,
    val pipelineErrors: Long,
    val lastStage: VoiceKeywordDiagnosticStage?,
    val lastActivityEpochMillis: Long?,
)

/**
 * 进程内计数器。每位玩家和全局各维护一份，方便多人局中单独定位，
 * 同时不让高频麦克风线程接触 Minecraft 状态。
 */
internal object VoiceKeywordDiagnostics {
    private class Counters {
        val microphonePackets = AtomicLong()
        val noTargetPackets = AtomicLong()
        val targetedPackets = AtomicLong()
        val modelNotReadyPackets = AtomicLong()
        val backendNotReadyPackets = AtomicLong()
        val queuedPackets = AtomicLong()
        val droppedPackets = AtomicLong()
        val resourceInitializations = AtomicLong()
        val decodedFrames = AtomicLong()
        val decodedSamples = AtomicLong()
        val finalizedSegments = AtomicLong()
        val emptyResults = AtomicLong()
        val invalidResults = AtomicLong()
        val textMismatches = AtomicLong()
        val lowConfidenceResults = AtomicLong()
        val lastAverageConfidence = AtomicReference<Double?>()
        val lastMinimumWordConfidence = AtomicReference<Double?>()
        val matchedResults = AtomicLong()
        val textFallbackMatches = AtomicLong()
        val deliveredDetections = AtomicLong()
        val rejectedDetections = AtomicLong()
        val settledDetections = AtomicLong()
        val rejectedSettlements = AtomicLong()
        val pipelineErrors = AtomicLong()
        val lastStage = AtomicReference<VoiceKeywordDiagnosticStage?>()
        val lastActivityEpochMillis = AtomicLong()

        fun record(
            stage: VoiceKeywordDiagnosticStage,
            samples: Int,
            averageConfidence: Double?,
            minimumWordConfidence: Double?,
        ) {
            when (stage) {
                VoiceKeywordDiagnosticStage.MICROPHONE_PACKET -> microphonePackets.incrementAndGet()
                VoiceKeywordDiagnosticStage.NO_ACTIVE_TARGET -> noTargetPackets.incrementAndGet()
                VoiceKeywordDiagnosticStage.TARGETED_PACKET -> targetedPackets.incrementAndGet()
                VoiceKeywordDiagnosticStage.MODEL_NOT_READY -> modelNotReadyPackets.incrementAndGet()
                VoiceKeywordDiagnosticStage.BACKEND_NOT_READY -> backendNotReadyPackets.incrementAndGet()
                VoiceKeywordDiagnosticStage.PACKET_QUEUED -> queuedPackets.incrementAndGet()
                VoiceKeywordDiagnosticStage.QUEUE_DROPPED -> droppedPackets.incrementAndGet()
                VoiceKeywordDiagnosticStage.RESOURCES_READY -> resourceInitializations.incrementAndGet()
                VoiceKeywordDiagnosticStage.AUDIO_DECODED -> {
                    decodedFrames.incrementAndGet()
                    decodedSamples.addAndGet(samples.toLong().coerceAtLeast(0L))
                }
                VoiceKeywordDiagnosticStage.SEGMENT_FINALIZED -> finalizedSegments.incrementAndGet()
                VoiceKeywordDiagnosticStage.RESULT_EMPTY -> emptyResults.incrementAndGet()
                VoiceKeywordDiagnosticStage.RESULT_INVALID -> invalidResults.incrementAndGet()
                VoiceKeywordDiagnosticStage.RESULT_TEXT_MISMATCH -> textMismatches.incrementAndGet()
                VoiceKeywordDiagnosticStage.RESULT_LOW_CONFIDENCE ->
                    lowConfidenceResults.incrementAndGet()
                VoiceKeywordDiagnosticStage.RESULT_MATCHED -> matchedResults.incrementAndGet()
                VoiceKeywordDiagnosticStage.RESULT_MATCHED_TEXT_FALLBACK -> {
                    matchedResults.incrementAndGet()
                    textFallbackMatches.incrementAndGet()
                }
                VoiceKeywordDiagnosticStage.DETECTION_DELIVERED ->
                    deliveredDetections.incrementAndGet()
                VoiceKeywordDiagnosticStage.DETECTION_REJECTED ->
                    rejectedDetections.incrementAndGet()
                VoiceKeywordDiagnosticStage.DDI_SETTLED -> settledDetections.incrementAndGet()
                VoiceKeywordDiagnosticStage.DDI_REJECTED -> rejectedSettlements.incrementAndGet()
                VoiceKeywordDiagnosticStage.PIPELINE_ERROR -> pipelineErrors.incrementAndGet()
            }
            if (stage == VoiceKeywordDiagnosticStage.RESULT_LOW_CONFIDENCE ||
                stage == VoiceKeywordDiagnosticStage.RESULT_MATCHED ||
                stage == VoiceKeywordDiagnosticStage.RESULT_MATCHED_TEXT_FALLBACK
            ) {
                lastAverageConfidence.set(averageConfidence?.takeIf(Double::isFinite))
                lastMinimumWordConfidence.set(
                    minimumWordConfidence?.takeIf(Double::isFinite)
                )
            }
            lastStage.set(stage)
            lastActivityEpochMillis.set(System.currentTimeMillis())
        }

        fun snapshot(
            playerId: UUID?,
            sinceEpochMillis: Long,
            connectedPlayers: Int,
            activeTargets: Int,
        ) = VoiceKeywordDiagnosticsSnapshot(
            playerId = playerId,
            sinceEpochMillis = sinceEpochMillis,
            connectedPlayers = connectedPlayers,
            activeTargets = activeTargets,
            microphonePackets = microphonePackets.get(),
            noTargetPackets = noTargetPackets.get(),
            targetedPackets = targetedPackets.get(),
            modelNotReadyPackets = modelNotReadyPackets.get(),
            backendNotReadyPackets = backendNotReadyPackets.get(),
            queuedPackets = queuedPackets.get(),
            droppedPackets = droppedPackets.get(),
            resourceInitializations = resourceInitializations.get(),
            decodedFrames = decodedFrames.get(),
            decodedSamples = decodedSamples.get(),
            finalizedSegments = finalizedSegments.get(),
            emptyResults = emptyResults.get(),
            invalidResults = invalidResults.get(),
            textMismatches = textMismatches.get(),
            lowConfidenceResults = lowConfidenceResults.get(),
            lastAverageConfidence = lastAverageConfidence.get(),
            lastMinimumWordConfidence = lastMinimumWordConfidence.get(),
            matchedResults = matchedResults.get(),
            textFallbackMatches = textFallbackMatches.get(),
            deliveredDetections = deliveredDetections.get(),
            rejectedDetections = rejectedDetections.get(),
            settledDetections = settledDetections.get(),
            rejectedSettlements = rejectedSettlements.get(),
            pipelineErrors = pipelineErrors.get(),
            lastStage = lastStage.get(),
            lastActivityEpochMillis = lastActivityEpochMillis.get().takeIf { it > 0L },
        )
    }

    @Volatile
    private var aggregate = Counters()
    private val perPlayer = ConcurrentHashMap<UUID, Counters>()
    private val sinceEpochMillis = AtomicLong(System.currentTimeMillis())

    fun record(
        playerId: UUID,
        stage: VoiceKeywordDiagnosticStage,
        samples: Int = 0,
        averageConfidence: Double? = null,
        minimumWordConfidence: Double? = null,
    ) {
        aggregate.record(stage, samples, averageConfidence, minimumWordConfidence)
        perPlayer.computeIfAbsent(playerId) { Counters() }.record(
            stage,
            samples,
            averageConfidence,
            minimumWordConfidence,
        )
    }

    fun snapshot(
        playerId: UUID?,
        connectedPlayers: Int,
        activeTargets: Int,
    ): VoiceKeywordDiagnosticsSnapshot {
        val counters = if (playerId == null) {
            aggregate
        } else {
            perPlayer[playerId] ?: Counters()
        }
        return counters.snapshot(
            playerId = playerId,
            sinceEpochMillis = sinceEpochMillis.get(),
            connectedPlayers = connectedPlayers,
            activeTargets = activeTargets,
        )
    }

    fun reset() {
        aggregate = Counters()
        perPlayer.clear()
        sinceEpochMillis.set(System.currentTimeMillis())
    }

    fun removePlayer(playerId: UUID) {
        perPlayer.remove(playerId)
    }
}
