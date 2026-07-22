package me.jfenn.bingo.integrations.voice

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** 仅开发期管理员工具使用的最终转写记录；不保存音频或原始 JSON。 */
data class VoiceKeywordDevelopmentTranscriptEntry(
    val playerId: UUID,
    val transcript: String,
    val outcome: VoiceKeywordTranscriptOutcome,
    val hasGameplayTarget: Boolean,
    val averageConfidence: Double? = null,
    val minimumWordConfidence: Double? = null,
    val capturedAtEpochMillis: Long,
)

data class VoiceKeywordDevelopmentDebugSnapshot(
    val enabled: Boolean,
    val revision: Long,
    val entries: List<VoiceKeywordDevelopmentTranscriptEntry>,
)

/**
 * 与玩家自助转写完全分离的开发期总线。启用时允许无词条玩家做自由转写；关闭即清空。
 */
internal class VoiceKeywordDevelopmentDebugBuffer(private val capacity: Int = 100) {
    private val enabled = AtomicBoolean(false)
    private val revision = AtomicLong()
    private val entries = ArrayDeque<VoiceKeywordDevelopmentTranscriptEntry>()

    fun enable() = synchronized(entries) {
        entries.clear()
        enabled.set(true)
        revision.incrementAndGet()
    }

    fun disable() = synchronized(entries) {
        enabled.set(false)
        entries.clear()
        revision.incrementAndGet()
    }

    fun isEnabled(): Boolean = enabled.get()

    fun snapshot(): VoiceKeywordDevelopmentDebugSnapshot = synchronized(entries) {
        VoiceKeywordDevelopmentDebugSnapshot(enabled.get(), revision.get(), entries.toList())
    }

    fun capture(playerId: UUID, target: VoiceKeywordTarget?, resultJson: String?, evaluation: VoiceKeywordResultEvaluation?) {
        if (!enabled.get()) return
        val matched = evaluation as? VoiceKeywordResultEvaluation.Matched
        val low = evaluation as? VoiceKeywordResultEvaluation.LowConfidence
        val outcome = evaluation?.outcome() ?: VoiceKeywordTranscriptOutcome.FREE_TRANSCRIPT
        synchronized(entries) {
            if (!enabled.get()) return
            while (entries.size >= capacity) entries.removeFirst()
            entries.addLast(VoiceKeywordDevelopmentTranscriptEntry(
                playerId, extractTranscript(resultJson), outcome, target != null,
                low?.average ?: matched?.match?.confidence?.takeUnless { matched.usedTextFallback },
                low?.minimum, System.currentTimeMillis(),
            ))
            revision.incrementAndGet()
        }
    }
}

internal fun extractTranscript(resultJson: String?): String {
    if (resultJson == null) return "（无最终结果）"
    val root = runCatching { Json.parseToJsonElement(resultJson) as? JsonObject }.getOrNull()
        ?: return "（结果 JSON 无效）"
    val text = root["text"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
    val words = (root["result"] as? JsonArray)?.mapNotNull { (it as? JsonObject)?.get("word")?.jsonPrimitive?.contentOrNull }.orEmpty()
    return (text.ifBlank { words.joinToString(" ") }.ifBlank { "（空）" }).take(96)
}

internal fun VoiceKeywordResultEvaluation.outcome(): VoiceKeywordTranscriptOutcome = when (this) {
    is VoiceKeywordResultEvaluation.Matched -> when {
        usedTextFallback -> VoiceKeywordTranscriptOutcome.MATCHED_TEXT_FALLBACK
        usedLightToneRelaxation -> VoiceKeywordTranscriptOutcome.MATCHED_LIGHT_TONE_RELAXED
        else -> VoiceKeywordTranscriptOutcome.MATCHED
    }
    is VoiceKeywordResultEvaluation.LowConfidence -> VoiceKeywordTranscriptOutcome.LOW_CONFIDENCE
    VoiceKeywordResultEvaluation.TextMismatch -> VoiceKeywordTranscriptOutcome.TEXT_MISMATCH
    VoiceKeywordResultEvaluation.EmptyResult -> VoiceKeywordTranscriptOutcome.EMPTY_RESULT
    VoiceKeywordResultEvaluation.InvalidJson -> VoiceKeywordTranscriptOutcome.INVALID_JSON
}
