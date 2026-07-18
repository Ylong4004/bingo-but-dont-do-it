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
    private const val MAX_SEGMENTED_HAN_RUN = 7
    private const val MAX_GRAMMAR_VARIANTS_PER_PHRASE = 64

    fun normalize(value: String): String {
        val normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
        return buildString {
            normalized.codePoints().forEachOrdered { codePoint ->
                if (Character.isLetterOrDigit(codePoint)) appendCodePoint(codePoint)
            }
        }
    }

    fun spokenPhrase(subjectId: String): String? {
        if (!subjectId.startsWith(VoiceKeywordTarget.VOICE_SUBJECT_PREFIX)) return null
        val raw = subjectId.removePrefix(VoiceKeywordTarget.VOICE_SUBJECT_PREFIX)
        val normalizedInput = Normalizer.normalize(raw, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
        val normalized = buildString {
            normalizedInput.codePoints().forEachOrdered { codePoint ->
                if (Character.isLetterOrDigit(codePoint)) {
                    appendCodePoint(codePoint)
                } else {
                    append(' ')
                }
            }
        }
            .trim()
            .replace(Regex("\\s+"), " ")
        return normalized.takeIf {
            it.isNotBlank() &&
                it != "unk" &&
                it.codePointCount(0, it.length) <= VoiceKeywordGrammar.MAX_PHRASE_LENGTH
        }
    }

    /**
     * 为每个内置别名和局内自定义词统一生成汉字/读音路径。Vosk 中文小模型的
     * 动态 grammar 不会自动给任意短语分词：例如词典包含“帮”“我”，却不一定
     * 包含“帮我”。汉字词元进入 Vosk 后会由模型词典映射为声学音节，因此逐字
     * 版本既是词元兜底，也是通用读音匹配路径，并非某几个词的硬编码修复。
     *
     * 原词和全句逐字版本拥有最高优先级，保证多段长自定义词即使触及数量上限，
     * 也不会丢失最关键的发音路径；剩余容量再用于复合词的其他分法。
     */
    fun grammarPhrases(spokenPhrase: String): Set<String> {
        val runs = phraseRuns(spokenPhrase)
        val phrases = linkedSetOf(spokenPhrase)
        val fullyTokenized = runs.flatMap { run ->
            if (run.isHan) {
                run.codePoints.map { codePointsToString(intArrayOf(it)) }
            } else {
                listOf(codePointsToString(run.codePoints))
            }
        }
        addTokenizedPhrase(phrases, fullyTokenized)
        addTokenizedPhrase(
            phrases,
            runs.map { run -> codePointsToString(run.codePoints) },
        )

        var combinations = listOf(emptyList<String>())
        runs.forEach { run ->
            val options = if (run.isHan) {
                hanSegmentations(run.codePoints)
            } else {
                listOf(listOf(codePointsToString(run.codePoints)))
            }
            val next = mutableListOf<List<String>>()
            loop@ for (prefix in combinations) {
                for (suffix in options) {
                    next += prefix + suffix
                    if (next.size >= MAX_GRAMMAR_VARIANTS_PER_PHRASE) break@loop
                }
            }
            combinations = next
        }

        for (tokens in combinations) {
            if (phrases.size >= MAX_GRAMMAR_VARIANTS_PER_PHRASE) break
            addTokenizedPhrase(phrases, tokens)
        }
        return phrases
    }

    private fun addTokenizedPhrase(destination: MutableSet<String>, tokens: List<String>) {
        val variant = tokens.joinToString(" ")
        if (variant.isNotBlank()) destination += variant
    }

    private data class PhraseRun(
        val isHan: Boolean,
        val codePoints: IntArray,
    )

    private fun phraseRuns(value: String): List<PhraseRun> {
        val result = mutableListOf<PhraseRun>()
        val current = mutableListOf<Int>()
        var currentIsHan: Boolean? = null
        fun flush() {
            val isHan = currentIsHan ?: return
            if (current.isNotEmpty()) result += PhraseRun(isHan, current.toIntArray())
            current.clear()
            currentIsHan = null
        }

        value.codePoints().forEachOrdered { codePoint ->
            if (Character.isWhitespace(codePoint)) {
                flush()
            } else {
                val isHan = Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN
                if (currentIsHan != null && currentIsHan != isHan) flush()
                currentIsHan = isHan
                current += codePoint
            }
        }
        flush()
        return result
    }

    private fun hanSegmentations(codePoints: IntArray): List<List<String>> {
        if (codePoints.size == 1) return listOf(listOf(codePointsToString(codePoints)))
        if (codePoints.size > MAX_SEGMENTED_HAN_RUN) {
            return listOf(
                listOf(codePointsToString(codePoints)),
                codePoints.map { codePointsToString(intArrayOf(it)) },
            )
        }

        return buildList {
            val boundaryMasks = 1 shl (codePoints.size - 1)
            val fullySplitMask = boundaryMasks - 1
            val masks = buildList {
                add(0)
                add(fullySplitMask)
                for (mask in 1 until fullySplitMask) add(mask)
            }.distinct()
            for (mask in masks) {
                val tokens = mutableListOf<String>()
                val token = StringBuilder()
                codePoints.forEachIndexed { index, codePoint ->
                    token.appendCodePoint(codePoint)
                    if (index == codePoints.lastIndex || mask and (1 shl index) != 0) {
                        tokens += token.toString()
                        token.setLength(0)
                    }
                }
                add(tokens)
            }
        }
    }

    private fun codePointsToString(codePoints: IntArray): String = buildString {
        codePoints.forEach(::appendCodePoint)
    }

    /**
     * 对“啊啊啊”“哈哈哈”这类纯重复语气词生成有限别名。小模型常合并或重复
     * 这些音节，因此三次“啊”可能返回两次或六次；普通非重复词绝不走此放宽。
     */
    fun repeatedPhraseAliases(normalizedPhrase: String): Set<String> {
        val codePoints = normalizedPhrase.codePoints().toArray()
        if (codePoints.size < 2 || codePoints.any { it != codePoints[0] }) return emptySet()
        val minimumCount = if (codePoints.size >= 3) 2 else codePoints.size
        val maximumCount = (codePoints.size * 2).coerceAtMost(VoiceKeywordGrammar.MAX_PHRASE_LENGTH)
        return (minimumCount..maximumCount).mapTo(linkedSetOf()) { count ->
            buildString {
                repeat(count) { appendCodePoint(codePoints[0]) }
            }
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
        private const val MAX_GRAMMAR_PHRASES = 256

        fun fromSubjects(subjectIds: Set<String>): VoiceKeywordGrammar? {
            if (subjectIds.isEmpty() || subjectIds.size > MAX_SUBJECTS) return null
            val subjectByPhrase = linkedMapOf<String, String>()
            val spokenPhrases = linkedSetOf<String>()
            val prepared = subjectIds.sorted().mapNotNull { subjectId ->
                val spoken = VoiceKeywordNormalizer.spokenPhrase(subjectId) ?: return@mapNotNull null
                val normalized = VoiceKeywordNormalizer.normalize(spoken)
                if (normalized.isBlank()) return@mapNotNull null
                Triple(subjectId, spoken, normalized)
            }
            prepared.forEach { (subjectId, spoken, normalized) ->
                // 若两个别名规范化后相同，则确定性地保留完整目标 ID；
                // 两个 ID 描述的是同一个口述词语。
                subjectByPhrase.putIfAbsent(normalized, subjectId)
                spokenPhrases += spoken
            }
            val variantLists = prepared.map { (_, spoken, _) ->
                VoiceKeywordNormalizer.grammarPhrases(spoken).filterNot { it == spoken }
            }
            // 每轮只从每个目标取一个变体，使最多 64 个内置/自定义别名都先获得
            // 全句逐字读音路径，然后才为短语继续补充其他复合词分法。
            var variantIndex = 0
            while (spokenPhrases.size < MAX_GRAMMAR_PHRASES) {
                var foundVariant = false
                for (variants in variantLists) {
                    val variant = variants.getOrNull(variantIndex) ?: continue
                    foundVariant = true
                    spokenPhrases += variant
                    if (spokenPhrases.size >= MAX_GRAMMAR_PHRASES) break
                }
                if (!foundVariant) break
                variantIndex++
            }
            // 精确目标先占位，重复语气词别名不得覆盖另一个目标的精确写法。
            prepared.forEach { (subjectId, _, normalized) ->
                VoiceKeywordNormalizer.repeatedPhraseAliases(normalized).forEach { alias ->
                    subjectByPhrase.putIfAbsent(alias, subjectId)
                }
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
