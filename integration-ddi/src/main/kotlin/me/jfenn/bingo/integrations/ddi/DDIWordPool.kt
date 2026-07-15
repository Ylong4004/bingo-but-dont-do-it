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
import kotlin.random.Random

/**
 * DDI 词条池 — 管理所有可用词条，支持随机抽取。
 * 移植自 Dont_do_it mod。
 */
class DDIWordPool(
    private val environment: IModEnvironment? = null,
) {

    data class WordEntry(
        val id: String,
        val displayText: String,
        val triggerType: DDITriggerType,
        /** Stable gameplay rule identity used for per-team no-repeat history. */
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
     * Draws a replacement that differs from the previous rule whenever the
     * pool has an alternative. This prevents an apparent no-op timer reroll
     * and avoids immediately dealing the same continuous trigger again.
     */
    fun drawReplacement(previous: WordEntry?): WordEntry {
        return drawAvailable(previous, emptySet(), emptySet()) ?: drawSingle()
    }

    /**
     * Draws without ever relaxing [triggeredRepeatKeys]. [softRepeatKeys]
     * prevents duplicate live rules within a team when the pool has room, but
     * may be relaxed before declaring the team's unique rule pool exhausted.
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
        return allWords.find { it.displayText == displayText }
    }

    /** 根据 ID 查找词条 */
    fun findById(id: String): WordEntry? {
        return allWords.find { it.id == id }
    }

    fun getAllWords(): List<WordEntry> = allWords.toList()

    fun size(): Int = allWords.size

    fun availableSize(): Int = availableWords().size

    private fun availableWords(): List<WordEntry> = allWords.filter { word ->
        word.rule.isAvailable { modId -> environment?.isModLoaded(modId) == true }
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
        // Legacy synonyms intentionally share enum-based repeat keys. New
        // parameterized definitions must describe one stable gameplay rule.
        val duplicateRepeatKeys = allWords
            .filter { it.category != "legacy" }
            .groupBy(WordEntry::repeatKey)
            .filterValues { it.size > 1 }
            .keys
        check(duplicateRepeatKeys.isEmpty()) { "Duplicate DDI repeat keys: $duplicateRepeatKeys" }
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

    private companion object {
        const val WORDS_SCHEMA = 1
        const val WORDS_RESOURCE =
            "/data/yet-another-minecraft-bingo/ddi/words_v1.json"
        const val LEGACY_CATEGORY = "legacy"
        const val DEFAULT_EXPANSION_WEIGHT = 0.3
        const val LEGACY_WEIGHT = 1.0
    }
}
