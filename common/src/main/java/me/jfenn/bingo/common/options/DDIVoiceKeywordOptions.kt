package me.jfenn.bingo.common.options

import java.text.Normalizer
import java.util.Locale

/** 设置墙输入、命令和运行时词条共用的校验规则。 */
object DDIVoiceKeywordOptions {
    const val MAX_CUSTOM_KEYWORDS = 32
    const val MAX_KEYWORD_CODE_POINTS = 32

    private val whitespace = Regex("\\s+")
    private val latinKeyword = Regex("[a-z][a-z0-9 _'-]{2,31}")
    private val chineseKeyword = Regex("[\\p{IsHan}a-zA-Z0-9 _'-]{2,32}")

    fun normalize(raw: String): String = Normalizer
        .normalize(raw, Normalizer.Form.NFKC)
        .trim()
        .replace(whitespace, " ")

    fun recognitionKey(raw: String): String = normalize(raw)
        .lowercase(Locale.ROOT)
        .filter { it.isLetterOrDigit() }

    fun validate(raw: String): String? {
        val normalized = normalize(raw)
        val codePoints = normalized.codePointCount(0, normalized.length)
        if (codePoints !in 2..MAX_KEYWORD_CODE_POINTS) return null
        if (normalized.any { it.isISOControl() }) return null
        if (normalized.all(Char::isDigit)) return null
        val lowered = normalized.lowercase(Locale.ROOT)
        if (!chineseKeyword.matches(normalized) && !latinKeyword.matches(lowered)) return null
        return normalized
    }

    fun normalizeList(values: Iterable<String>): List<String> = values
        .mapNotNull(::validate)
        .distinctBy(::recognitionKey)
        .take(MAX_CUSTOM_KEYWORDS)
}
