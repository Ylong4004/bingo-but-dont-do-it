package me.jfenn.bingo.common.options

import java.text.Normalizer
import java.util.Locale

/** 设置墙输入、命令和运行时词条共用的校验规则。 */
object DDIVoiceKeywordOptions {
    const val MAX_KEYWORD_CODE_POINTS = 32
    /**
     * 这是持久化配置的防护预算，不是面向玩家的“词条数量上限”。
     * 以 Unicode code point 计算，避免大量自定义文本撑大选项同步或存档。
     */
    const val MAX_TOTAL_CUSTOM_KEYWORD_CODE_POINTS = 8_192

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

    fun totalCodePointCount(values: Iterable<String>): Long = values.sumOf { value ->
        value.codePointCount(0, value.length).toLong()
    }

    fun isWithinTotalBudget(values: Iterable<String>): Boolean =
        totalCodePointCount(values) <= MAX_TOTAL_CUSTOM_KEYWORD_CODE_POINTS

    fun normalizeList(values: Iterable<String>): List<String> = values
        .mapNotNull(::validate)
        .distinctBy(::recognitionKey)
}
