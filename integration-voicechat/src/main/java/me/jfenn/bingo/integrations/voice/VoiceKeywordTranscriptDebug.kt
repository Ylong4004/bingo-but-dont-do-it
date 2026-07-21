package me.jfenn.bingo.integrations.voice

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil

/** 仅在玩家主动开启时记录的最终转写结果分类。 */
enum class VoiceKeywordTranscriptOutcome {
    MATCHED,
    MATCHED_TEXT_FALLBACK,
    LOW_CONFIDENCE,
    TEXT_MISMATCH,
    EMPTY_RESULT,
    INVALID_JSON,
}

/**
 * 不含音频或原始 JSON 的单次最终转写。此对象只存在于服务端内存的短时调试缓冲，
 * 不会写入日志、配置、存档或网络同步包。
 */
data class VoiceKeywordTranscriptEntry(
    val transcript: String,
    val outcome: VoiceKeywordTranscriptOutcome,
    val averageConfidence: Double? = null,
    val minimumWordConfidence: Double? = null,
)

data class VoiceKeywordTranscriptSnapshot(
    val enabled: Boolean,
    val secondsRemaining: Int,
    val entries: List<VoiceKeywordTranscriptEntry> = emptyList(),
)

/**
 * 每名玩家独立的、显式启用的短时调试缓冲。
 *
 * ASR 工作线程可直接写入，所以每个会话各自加锁；会话过期后会立即移除，防止 UUID
 * 明细在长期服务器中积累。最终文本保留原样的短片段而非原始 JSON，以便排查连读和
 * 置信度问题，同时避免隐式扩大数据留存范围。
 */
internal class VoiceKeywordTranscriptBuffer(
    private val nowNanos: () -> Long = System::nanoTime,
    private val sessionDurationNanos: Long = 10L * 60L * 1_000_000_000L,
    private val capacity: Int = 12,
) {
    private data class Session(
        val expiresAtNanos: Long,
        val entries: ArrayDeque<VoiceKeywordTranscriptEntry> = ArrayDeque(),
    )

    private val sessions = ConcurrentHashMap<UUID, Session>()

    fun enable(playerId: UUID): VoiceKeywordTranscriptSnapshot {
        val now = nowNanos()
        val session = Session(now + sessionDurationNanos)
        sessions[playerId] = session
        return snapshot(playerId, now)
    }

    fun disable(playerId: UUID) {
        sessions.remove(playerId)
    }

    fun clear() {
        sessions.clear()
    }

    fun snapshot(playerId: UUID): VoiceKeywordTranscriptSnapshot = snapshot(playerId, nowNanos())

    fun capture(
        playerId: UUID,
        resultJson: String?,
        evaluation: VoiceKeywordResultEvaluation,
    ) {
        val now = nowNanos()
        val session = activeSession(playerId, now) ?: return
        val lowConfidence = evaluation as? VoiceKeywordResultEvaluation.LowConfidence
        val matched = evaluation as? VoiceKeywordResultEvaluation.Matched
        val entry = VoiceKeywordTranscriptEntry(
            transcript = extractTranscript(resultJson),
            outcome = evaluation.outcome(),
            averageConfidence = lowConfidence?.average
                ?: matched?.match?.confidence?.takeUnless { matched.usedTextFallback },
            minimumWordConfidence = lowConfidence?.minimum,
        )
        synchronized(session) {
            // 会话可能刚好在解析 JSON 时过期，过期结果绝不重新激活会话。
            if (now >= session.expiresAtNanos) {
                sessions.remove(playerId, session)
                return
            }
            while (session.entries.size >= capacity) session.entries.removeFirst()
            session.entries.addLast(entry)
        }
    }

    private fun snapshot(playerId: UUID, now: Long): VoiceKeywordTranscriptSnapshot {
        val session = activeSession(playerId, now)
            ?: return VoiceKeywordTranscriptSnapshot(enabled = false, secondsRemaining = 0)
        synchronized(session) {
            val seconds = ceil((session.expiresAtNanos - now).toDouble() / 1_000_000_000.0)
                .toInt()
                .coerceAtLeast(0)
            return VoiceKeywordTranscriptSnapshot(
                enabled = seconds > 0,
                secondsRemaining = seconds,
                entries = session.entries.toList(),
            )
        }
    }

    private fun activeSession(playerId: UUID, now: Long): Session? {
        val session = sessions[playerId] ?: return null
        if (now >= session.expiresAtNanos) {
            sessions.remove(playerId, session)
            return null
        }
        return session
    }

    private fun extractTranscript(resultJson: String?): String {
        if (resultJson == null) return "（无最终结果）"
        val root = runCatching { Json.parseToJsonElement(resultJson) as? JsonObject }.getOrNull()
            ?: return "（结果 JSON 无效）"
        val directText = root["text"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val resultWords = (root["result"] as? JsonArray)
            ?.mapNotNull { (it as? JsonObject)?.get("word")?.jsonPrimitive?.contentOrNull }
            .orEmpty()
        return boundText(directText.ifBlank { resultWords.joinToString(" ") }.ifBlank { "（空）" })
    }

    private fun boundText(value: String): String {
        val trimmed = value.trim()
        val limit = 96
        val count = trimmed.codePointCount(0, trimmed.length)
        if (count <= limit) return trimmed
        val end = trimmed.offsetByCodePoints(0, limit)
        return "${trimmed.substring(0, end)}…"
    }

    private fun VoiceKeywordResultEvaluation.outcome(): VoiceKeywordTranscriptOutcome = when (this) {
        is VoiceKeywordResultEvaluation.Matched -> if (usedTextFallback) {
            VoiceKeywordTranscriptOutcome.MATCHED_TEXT_FALLBACK
        } else {
            VoiceKeywordTranscriptOutcome.MATCHED
        }
        is VoiceKeywordResultEvaluation.LowConfidence -> VoiceKeywordTranscriptOutcome.LOW_CONFIDENCE
        VoiceKeywordResultEvaluation.TextMismatch -> VoiceKeywordTranscriptOutcome.TEXT_MISMATCH
        VoiceKeywordResultEvaluation.EmptyResult -> VoiceKeywordTranscriptOutcome.EMPTY_RESULT
        VoiceKeywordResultEvaluation.InvalidJson -> VoiceKeywordTranscriptOutcome.INVALID_JSON
    }
}
