package me.jfenn.bingo.integrations.ddi

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.jfenn.bingo.platform.IModEnvironment
import me.jfenn.bingo.common.options.DDIVoiceKeywordOptions
import kotlin.random.Random

/**
 * DDI 词条池 — 管理所有可用词条，支持随机抽取。
 * 移植自 Dont_do_it 模组。
 */
class DDIWordPool(
    private val environment: IModEnvironment? = null,
) {

    data class WordEntry(
        val id: String,
        val displayText: String,
        val triggerType: DDITriggerType,
        /** 用于队伍不重复历史记录的稳定玩法规则标识。 */
        val repeatKey: String = triggerType.name,
        val displayKey: String? = null,
        val category: String = "legacy",
        val weight: Double = 1.0,
        val rule: DDIRuleDefinition = DDIRuleDefinition.legacy(triggerType),
    ) {
        init {
            require(id.isNotBlank()) { "DDI word ID cannot be blank" }
            require(displayText.isNotBlank()) { "DDI word display text cannot be blank" }
            require(repeatKey.isNotBlank()) { "DDI repeat key cannot be blank" }
            require(weight > 0.0 && weight.isFinite()) { "DDI word weight must be finite and positive" }
        }
    }

    private val allWords = mutableListOf<WordEntry>()
    private var customVoiceWords: List<WordEntry> = emptyList()
    private var disabledCategories: Set<String> = emptySet()
    private var disabledWordIds: Set<String> = emptySet()
    private val random = Random.Default

    init {
        loadWords()
        validatePool()
    }

    /** 为指定数量的玩家各抽取一个不重复的词条 */
    fun drawWords(count: Int): List<WordEntry> {
        val candidates = availableWords().toMutableList()
        if (count > candidates.size) {
            throw IllegalStateException("玩家数量($count)超过可用词条池大小(${candidates.size})")
        }
        return buildList(count) {
            repeat(count) {
                val selected = weightedRandom(candidates)
                add(selected)
                candidates.remove(selected)
            }
        }
    }

    /** 随机抽取一个词条 */
    fun drawSingle(): WordEntry {
        return weightedRandom(availableWords())
    }

    /**
     * 只要词池中存在替代项，就抽取一条与上一条规则不同的词条。
     * 这样可避免计时器看似重抽却没有变化，也可避免立即再次发放同一持续触发规则。
     */
    fun drawReplacement(previous: WordEntry?): WordEntry {
        return drawAvailable(previous, emptySet(), emptySet()) ?: drawSingle()
    }

    /**
     * 抽取时绝不放宽 [triggeredRepeatKeys]。[softRepeatKeys] 会在词池空间充足时
     * 防止同一队伍出现重复的当前规则，但在判定该队独立规则池耗尽前可以放宽。
     */
    fun drawAvailable(
        previous: WordEntry?,
        triggeredRepeatKeys: Set<String>,
        softRepeatKeys: Set<String>,
        predicate: (WordEntry) -> Boolean = { true },
    ): WordEntry? {
        val hardCandidates = availableWords().filter { word ->
            word.repeatKey !in triggeredRepeatKeys && predicate(word)
        }
        if (hardCandidates.isEmpty()) return null

        val preferred = hardCandidates.filter { candidate ->
            candidate.repeatKey !in softRepeatKeys &&
                candidate.repeatKey != previous?.repeatKey
        }
        if (preferred.isNotEmpty()) return weightedRandom(preferred)

        val withoutPrevious = hardCandidates.filter { it.repeatKey != previous?.repeatKey }
        if (withoutPrevious.isNotEmpty()) return weightedRandom(withoutPrevious)
        return weightedRandom(hardCandidates)
    }

    /** 根据显示文本查找词条 */
    fun findByDisplayText(displayText: String): WordEntry? {
        return (allWords + customVoiceWords).find { it.displayText == displayText }
    }

    /** 根据 ID 查找词条 */
    fun findById(id: String): WordEntry? {
        return (allWords + customVoiceWords).find { it.id == id }
    }

    fun getAllWords(): List<WordEntry> = allWords.toList() + customVoiceWords

    fun size(): Int = allWords.size + customVoiceWords.size

    fun availableSize(): Int = availableWords().size

    fun isEnabled(word: WordEntry): Boolean =
        word.category !in disabledCategories && word.id !in disabledWordIds

    /**
     * 默认所有词条均可抽取；配置只记录被关闭的类别或词条，以便新词条自动加入默认池。
     * 返回值用于避免无关设置变化触发一次不必要的重抽。
     */
    fun setWordSelection(
        disabledCategories: Set<String>,
        disabledWordIds: Set<String>,
    ): Boolean {
        val normalizedCategories = disabledCategories.toSet()
        val normalizedWordIds = disabledWordIds.toSet()
        if (this.disabledCategories == normalizedCategories && this.disabledWordIds == normalizedWordIds) {
            return false
        }
        this.disabledCategories = normalizedCategories
        this.disabledWordIds = normalizedWordIds
        return true
    }

    private fun availableWords(): List<WordEntry> = (allWords + customVoiceWords).filter { word ->
        isEnabled(word) && word.rule.isAvailable { modId -> environment?.isModLoaded(modId) == true }
    }

    /**
     * 替换运行时定义的语音词条。静态条目仍仅来自 words_v1.json；
     * 此列表是单局选项的不可变投影。
     */
    fun setCustomVoiceKeywords(values: Iterable<String>): List<WordEntry> {
        val builtInRecognitionKeys = allWords.asSequence()
            .filter { it.category == VOICE_CATEGORY }
            .flatMap { it.rule.subjectIds.asSequence() }
            .map { it.removePrefix(VOICE_SUBJECT_PREFIX) }
            .map(DDIVoiceKeywordOptions::recognitionKey)
            .toSet()

        customVoiceWords = DDIVoiceKeywordOptions.normalizeList(values)
            .filterNot { DDIVoiceKeywordOptions.recognitionKey(it) in builtInRecognitionKeys }
            .map(::customVoiceWord)
        return customVoiceWords.toList()
    }

    private fun customVoiceWord(keyword: String): WordEntry {
        val id = checkNotNull(DDIVoiceKeywordOptions.customWordId(keyword)) {
            "Custom voice keyword must have been validated before creating a word entry"
        }
        val suffix = id.removePrefix("voice_custom_")
        return WordEntry(
            id = id,
            displayText = "说出“$keyword”",
            triggerType = DDITriggerType.SPEAK_KEYWORD,
            repeatKey = "voice:custom:$suffix",
            category = VOICE_CATEGORY,
            weight = VOICE_WEIGHT,
            rule = DDIRuleDefinition(
                signalKind = DDISignalKind.VOICE_KEYWORD_SPOKEN,
                subjectIds = setOf("$VOICE_SUBJECT_PREFIX$keyword"),
                requiredMods = setOf("voicechat"),
            ),
        )
    }

    private fun weightedRandom(candidates: List<WordEntry>): WordEntry {
        require(candidates.isNotEmpty()) { "DDI word pool has no available entries" }
        val totalWeight = candidates.sumOf(WordEntry::weight)
        var cursor = random.nextDouble(totalWeight)
        for (candidate in candidates) {
            cursor -= candidate.weight
            if (cursor < 0.0) return candidate
        }
        return candidates.last()
    }

    private fun loadWords() {
        val resource = javaClass.getResourceAsStream(WORDS_RESOURCE)
            ?: error("Missing built-in DDI words resource: $WORDS_RESOURCE")
        val root = resource.bufferedReader(Charsets.UTF_8).use { reader ->
            Json.parseToJsonElement(reader.readText()).jsonObject
        }
        check(root.requiredInt("schemaVersion") == WORDS_SCHEMA) {
            "Unsupported DDI words schema in $WORDS_RESOURCE"
        }

        root.getValue("words").jsonArray.forEach { element ->
            val json = element.jsonObject
            val type = DDITriggerType.valueOf(json.requiredString("triggerType"))
            val category = json.optionalString("category") ?: LEGACY_CATEGORY
            val rule = json["rule"]?.jsonObject?.toRuleDefinition()
                ?: DDIRuleDefinition.legacy(type)
            allWords += WordEntry(
                id = json.requiredString("id"),
                displayText = json.requiredString("displayText"),
                triggerType = type,
                repeatKey = json.optionalString("repeatKey") ?: type.name,
                displayKey = json.optionalString("displayKey"),
                category = category,
                weight = json["weight"]?.jsonPrimitive?.double
                    ?: if (category == LEGACY_CATEGORY) LEGACY_WEIGHT else DEFAULT_EXPANSION_WEIGHT,
                rule = rule,
            )
        }
    }

    private fun JsonObject.toRuleDefinition(): DDIRuleDefinition = DDIRuleDefinition(
        signalKind = DDISignalKind.valueOf(requiredString("signal")),
        legacyTrigger = optionalString("legacyTrigger")?.let(DDITriggerType::valueOf),
        subjectIds = stringSet("subjectIds"),
        subjectTags = stringSet("subjectTags"),
        requireBlockItem = optionalString("requireBlockItem")?.toBooleanStrict(),
        requiredProgress = optionalInt("requiredProgress") ?: 1,
        progressMode = optionalString("progressMode")
            ?.let(DDIProgressMode::valueOf)
            ?: DDIProgressMode.ACTIONS,
        deadlineBehavior = optionalString("deadlineBehavior")
            ?.let(DDIDeadlineBehavior::valueOf)
            ?: DDIDeadlineBehavior.REROLL,
        matchBehavior = optionalString("matchBehavior")
            ?.let(DDIMatchBehavior::valueOf)
            ?: DDIMatchBehavior.VIOLATION,
        requiredMods = stringSet("requiredMods"),
    )

    private fun validatePool() {
        val duplicateIds = allWords.groupBy(WordEntry::id).filterValues { it.size > 1 }.keys
        check(duplicateIds.isEmpty()) { "Duplicate DDI word IDs: $duplicateIds" }
        // 旧版同义词有意共享基于枚举的重复键。
        // 新增参数化定义必须描述一条稳定的玩法规则。
        val duplicateRepeatKeys = allWords
            .filter { it.category != "legacy" }
            .groupBy(WordEntry::repeatKey)
            .filterValues { it.size > 1 }
            .keys
        check(duplicateRepeatKeys.isEmpty()) { "Duplicate DDI repeat keys: $duplicateRepeatKeys" }

        // 语音词条必须统一进入 VoiceKeywordTarget -> VoiceKeywordGrammar，避免后续
        // 新增 JSON 时因分类、信号或命名空间写错而静默绕过汉字/读音匹配。
        val invalidVoiceWords = allWords.filter { word ->
            val isVoiceCategory = word.category == VOICE_CATEGORY
            val isVoiceSignal = word.rule.signalKind == DDISignalKind.VOICE_KEYWORD_SPOKEN
            if (!isVoiceCategory) {
                isVoiceSignal
            } else {
                !isVoiceSignal ||
                    word.triggerType != DDITriggerType.SPEAK_KEYWORD ||
                    word.rule.subjectIds.isEmpty() ||
                    word.rule.subjectIds.any { !it.startsWith(VOICE_SUBJECT_PREFIX) } ||
                    "voicechat" !in word.rule.requiredMods
            }
        }
        check(invalidVoiceWords.isEmpty()) {
            "Invalid DDI voice word definitions: ${invalidVoiceWords.map(WordEntry::id)}"
        }
    }

    private fun JsonObject.requiredString(name: String): String =
        getValue(name).jsonPrimitive.content

    private fun JsonObject.optionalString(name: String): String? =
        get(name)?.jsonPrimitive?.contentOrNull

    private fun JsonObject.requiredInt(name: String): Int =
        getValue(name).jsonPrimitive.int

    private fun JsonObject.optionalInt(name: String): Int? =
        get(name)?.jsonPrimitive?.int

    private fun JsonObject.stringSet(name: String): Set<String> =
        get(name)?.jsonArray?.mapTo(linkedSetOf()) { it.jsonPrimitive.content } ?: emptySet()

    companion object {
        const val WORDS_SCHEMA = 1
        const val WORDS_RESOURCE =
            "/data/yet-another-minecraft-bingo/ddi/words_v1.json"
        const val LEGACY_CATEGORY = "legacy"
        const val VOICE_CATEGORY = "voice"
        const val VOICE_SUBJECT_PREFIX = "voice:"
        const val DEFAULT_EXPANSION_WEIGHT = 0.3
        const val VOICE_WEIGHT = 0.45
        const val LEGACY_WEIGHT = 1.0
    }
}
