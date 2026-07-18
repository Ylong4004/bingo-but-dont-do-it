package me.jfenn.bingo.integrations.voice

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.text.Normalizer
import java.util.Locale

internal object VoiceKeywordNormalizer {
    fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT)
        .filter { Character.isLetterOrDigit(it) }

    fun spokenPhrase(subjectId: String): String? {
        if (!subjectId.startsWith(VoiceKeywordTarget.VOICE_SUBJECT_PREFIX)) return null
        val raw = subjectId.removePrefix(VoiceKeywordTarget.VOICE_SUBJECT_PREFIX)
        val normalized = Normalizer.normalize(raw, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .map { character ->
                when {
                    Character.isLetterOrDigit(character) -> character
                    character.isWhitespace() -> ' '
                    else -> ' '
                }
            }
            .joinToString("")
            .trim()
            .replace(Regex("\\s+"), " ")
        return normalized.takeIf {
            it.isNotBlank() &&
                it != "unk" &&
                it.length <= VoiceKeywordGrammar.MAX_PHRASE_LENGTH
        }
    }
}

internal data class VoiceKeywordGrammar(
    val grammarJson: String,
    val subjectByNormalizedPhrase: Map<String, String>,
) {
    companion object {
        const val MAX_SUBJECTS = 64
        const val MAX_PHRASE_LENGTH = 64

        fun fromSubjects(subjectIds: Set<String>): VoiceKeywordGrammar? {
            if (subjectIds.isEmpty() || subjectIds.size > MAX_SUBJECTS) return null
            val subjectByPhrase = linkedMapOf<String, String>()
            val spokenPhrases = linkedSetOf<String>()
            subjectIds.sorted().forEach { subjectId ->
                val spoken = VoiceKeywordNormalizer.spokenPhrase(subjectId) ?: return@forEach
                val normalized = VoiceKeywordNormalizer.normalize(spoken)
                if (normalized.isBlank()) return@forEach
                // 若两个别名规范化后相同，则确定性地保留完整目标 ID；
                // 两个 ID 描述的是同一个口述词语。
                subjectByPhrase.putIfAbsent(normalized, subjectId)
                spokenPhrases += spoken
            }
            if (subjectByPhrase.isEmpty()) return null
            val values = spokenPhrases.map(::JsonPrimitive) + JsonPrimitive("[unk]")
            return VoiceKeywordGrammar(
                grammarJson = JsonArray(values).toString(),
                subjectByNormalizedPhrase = subjectByPhrase,
            )
        }
    }
}

internal data class VoiceKeywordMatch(
    val subjectId: String,
    val confidence: Double,
)

/** 最终识别结果的隐私安全分类；绝不保存 Vosk 返回的原始文本。 */
internal sealed interface VoiceKeywordResultEvaluation {
    data class Matched(
        val match: VoiceKeywordMatch,
        val usedTextFallback: Boolean = false,
    ) : VoiceKeywordResultEvaluation

    data object InvalidJson : VoiceKeywordResultEvaluation
    data object EmptyResult : VoiceKeywordResultEvaluation
    data object TextMismatch : VoiceKeywordResultEvaluation
    data class LowConfidence(
        val average: Double,
        val minimum: Double,
    ) : VoiceKeywordResultEvaluation
}

/** 保守的最终结果匹配器；绝不接受 Vosk 的部分识别结果。 */
internal object VoiceKeywordResultMatcher {
    // 中文小模型在受限语法中经常给出 0.5~0.8 的置信度。
    // 完整短语仍必须与唯一目标精确匹配，所以无需使用通用听写场景的高阈值。
    const val DEFAULT_AVERAGE_CONFIDENCE = 0.55
    const val DEFAULT_MINIMUM_WORD_CONFIDENCE = 0.30

    fun matchFinalResult(
        resultJson: String,
        grammar: VoiceKeywordGrammar,
        minimumAverageConfidence: Double = DEFAULT_AVERAGE_CONFIDENCE,
        minimumWordConfidence: Double = DEFAULT_MINIMUM_WORD_CONFIDENCE,
    ): VoiceKeywordMatch? = when (
        val evaluation = evaluateFinalResult(
            resultJson,
            grammar,
            minimumAverageConfidence,
            minimumWordConfidence,
        )
    ) {
        is VoiceKeywordResultEvaluation.Matched -> evaluation.match
        else -> null
    }

    fun evaluateFinalResult(
        resultJson: String,
        grammar: VoiceKeywordGrammar,
        minimumAverageConfidence: Double = DEFAULT_AVERAGE_CONFIDENCE,
        minimumWordConfidence: Double = DEFAULT_MINIMUM_WORD_CONFIDENCE,
    ): VoiceKeywordResultEvaluation {
        val root = runCatching { Json.parseToJsonElement(resultJson) as? JsonObject }
            .getOrNull() ?: return VoiceKeywordResultEvaluation.InvalidJson
        val result = root["result"] as? JsonArray
        if (result == null || result.isEmpty()) {
            // 某些 Vosk/原生库组合不会返回逐词数组。仅在它确实是最终结果，
            // 且最终文本与受限语法中的完整目标精确一致时才采用此回退。
            val normalizedText = root["text"]
                ?.jsonPrimitive
                ?.contentOrNull
                ?.let(VoiceKeywordNormalizer::normalize)
                .orEmpty()
            if (normalizedText.isBlank()) return VoiceKeywordResultEvaluation.EmptyResult
            val subjectId = grammar.subjectByNormalizedPhrase[normalizedText]
                ?: return VoiceKeywordResultEvaluation.TextMismatch
            return VoiceKeywordResultEvaluation.Matched(
                match = VoiceKeywordMatch(subjectId, confidence = 0.0),
                usedTextFallback = true,
            )
        }
        val words = result.mapNotNull { element ->
            val word = element as? JsonObject ?: return@mapNotNull null
            val text = word["word"]?.jsonPrimitive?.content ?: return@mapNotNull null
            if (text == "[unk]") return@mapNotNull null
            val confidence = word["conf"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            RecognizedWord(text, confidence.coerceIn(0.0, 1.0))
        }
        if (words.isEmpty()) return VoiceKeywordResultEvaluation.EmptyResult

        val normalized = VoiceKeywordNormalizer.normalize(words.joinToString("") { it.text })
        val subjectId = grammar.subjectByNormalizedPhrase[normalized]
            ?: return VoiceKeywordResultEvaluation.TextMismatch
        val minimum = words.minOf { it.confidence }
        val average = words.sumOf { it.confidence } / words.size
        if (minimum < minimumWordConfidence || average < minimumAverageConfidence) {
            return VoiceKeywordResultEvaluation.LowConfidence(average, minimum)
        }
        return VoiceKeywordResultEvaluation.Matched(VoiceKeywordMatch(subjectId, average))
    }

    private data class RecognizedWord(val text: String, val confidence: Double)
}
