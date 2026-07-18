package me.jfenn.bingo.integrations.ddi

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.jfenn.bingo.common.team.BingoTeamKey
import org.junit.jupiter.api.Test
import java.security.MessageDigest

class DDIWordPoolTest {

    private val pool = DDIWordPool()

    @Test
    fun `expanded pool has unique ids and preserves every legacy trigger`() {
        val words = pool.getAllWords()

        assertThat(words).hasSize(356)
        assertThat(words.map { it.id }.toSet()).hasSize(356)
        assertThat(words.map { it.triggerType }.toSet())
            .isEqualTo(DDITriggerType.entries.toSet())
    }

    @Test
    fun `object and block expansion keeps its reviewed category counts`() {
        val reviewedCategories = setOf("craft", "pickup", "drop", "place", "stand", "break", "hold")
        val counts = pool.getAllWords()
            .filter { it.category in reviewedCategories }
            .groupingBy { it.category }
            .eachCount()

        assertThat(counts).isEqualTo(
            mapOf(
                "craft" to 19,
                "pickup" to 20,
                "drop" to 14,
                "place" to 12,
                "stand" to 12,
                "break" to 15,
                "hold" to 18,
            )
        )
    }

    @Test
    fun `Bingo movement and player interaction batches have reviewed counts`() {
        val counts = pool.getAllWords()
            .filter { it.category in setOf("bingo", "movement", "player_interaction") }
            .groupingBy { it.category }
            .eachCount()

        assertThat(counts).isEqualTo(
            mapOf(
                "bingo" to 29,
                "movement" to 12,
                "player_interaction" to 4,
            )
        )
    }

    @Test
    fun `voice batch has reviewed aliases and remains optional`() {
        val voiceWords = pool.getAllWords().filter { it.category == "voice" }

        assertThat(voiceWords).hasSize(40)
        assertThat(voiceWords.all { it.rule.signalKind == DDISignalKind.VOICE_KEYWORD_SPOKEN })
            .isEqualTo(true)
        assertThat(voiceWords.all { it.triggerType == DDITriggerType.SPEAK_KEYWORD })
            .isEqualTo(true)
        assertThat(voiceWords.all { word ->
            word.rule.subjectIds.isNotEmpty() &&
                word.rule.subjectIds.all { it.startsWith(DDIWordPool.VOICE_SUBJECT_PREFIX) }
        }).isEqualTo(true)
        assertThat(voiceWords.all { "voicechat" in it.rule.requiredMods })
            .isEqualTo(true)
        assertThat(pool.availableSize()).isEqualTo(316)
        assertThat(pool.findById("voice_diamond")?.rule?.subjectIds)
            .isEqualTo(setOf("voice:钻石", "voice:钻石矿"))
    }

    @Test
    fun `custom voice words are normalized stable and do not duplicate built-ins`() {
        val first = pool.setCustomVoiceKeywords(listOf("  远古残骸  ", "远古残骸", "钻石"))
        val firstId = first.single().id
        val second = pool.setCustomVoiceKeywords(listOf("远古残骸"))

        assertThat(first.single().displayText).isEqualTo("说出“远古残骸”")
        assertThat(first.single().category).isEqualTo(DDIWordPool.VOICE_CATEGORY)
        assertThat(first.single().rule.signalKind).isEqualTo(DDISignalKind.VOICE_KEYWORD_SPOKEN)
        assertThat(first.single().rule.subjectIds).isEqualTo(setOf("voice:远古残骸"))
        assertThat(second.single().id).isEqualTo(firstId)
        assertThat(pool.findById(firstId)).isEqualTo(second.single())
    }

    @Test
    fun `single JSON catalog contains every runtime word in the same order`() {
        val catalog = catalogWords()

        assertThat(catalog.map { it.getValue("id").jsonPrimitive.content })
            .isEqualTo(pool.getAllWords().map { it.id })
        assertThat(catalog.any { "replaceId" in it }).isEqualTo(false)
    }

    @Test
    fun `omitted catalog fields preserve legacy defaults`() {
        val sneak = pool.findById("sneak_01")!!

        assertThat(sneak.repeatKey).isEqualTo(DDITriggerType.SNEAK.name)
        assertThat(sneak.category).isEqualTo("legacy")
        assertThat(sneak.weight).isEqualTo(1.0)
        assertThat(sneak.rule).isEqualTo(DDIRuleDefinition.legacy(DDITriggerType.SNEAK))
    }

    @Test
    fun `unified catalog preserves the original 271 word semantic baseline`() {
        val canonical = pool.getAllWords().take(271).joinToString("\n") { word ->
            val rule = word.rule
            listOf(
                word.id,
                word.displayText,
                word.triggerType.name,
                word.repeatKey,
                word.displayKey.orEmpty(),
                word.category,
                word.weight.toString(),
                rule.signalKind.name,
                rule.legacyTrigger?.name.orEmpty(),
                rule.subjectIds.sorted().joinToString(","),
                rule.subjectTags.sorted().joinToString(","),
                rule.requireBlockItem?.toString().orEmpty(),
                rule.requiredProgress.toString(),
                rule.progressMode.name,
                rule.deadlineBehavior.name,
                rule.matchBehavior.name,
                rule.requiredMods.sorted().joinToString(","),
            ).joinToString("\u001f")
        }
        val fingerprint = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        assertThat(fingerprint)
            .isEqualTo("87b292627d3a1731511ac11e1ecef3ddb18c6c9b618561877b39726ffeb4548a")
    }

    @Test
    fun `parameterized rules have unique repeat keys`() {
        val words = pool.getAllWords().filter { it.category != "legacy" }

        assertThat(words.map { it.repeatKey }.toSet()).hasSize(words.size)
    }

    @Test
    fun `migrated words keep legacy weight while net new words use expansion weight`() {
        assertThat(pool.findById("craft_table_01")?.weight).isEqualTo(1.0)
        assertThat(pool.findById("craft_planks_01")?.weight).isEqualTo(0.3)
    }

    @Test
    fun `replacement never immediately repeats the previous trigger`() {
        val previous = pool.getAllWords().first()

        repeat(100) {
            assertThat(pool.drawReplacement(previous).repeatKey)
                .isNotEqualTo(previous.repeatKey)
        }
    }

    @Test
    fun `hard team history is never relaxed`() {
        val chat = pool.getAllWords().first { it.triggerType == DDITriggerType.CHAT }

        repeat(100) {
            val drawn = pool.drawAvailable(
                previous = null,
                triggeredRepeatKeys = setOf(chat.repeatKey),
                softRepeatKeys = pool.getAllWords().map { it.repeatKey }.toSet(),
            )
            assertThat(drawn?.repeatKey).isNotEqualTo(chat.repeatKey)
        }

        assertThat(
            pool.drawAvailable(
                previous = null,
                triggeredRepeatKeys = pool.getAllWords().map { it.repeatKey }.toSet(),
                softRepeatKeys = emptySet(),
            )
        ).isNull()
    }

    @Test
    fun `triggered repeat keys are shared within a team but isolated between teams`() {
        val history = DDITeamWordHistory()
        val orange = BingoTeamKey("bingo_orange")
        val blue = BingoTeamKey("bingo_blue")
        val word = pool.getAllWords().first()

        history.record(orange, word)

        assertThat(word.repeatKey in history.get(orange)).isEqualTo(true)
        assertThat(word.repeatKey in history.get(blue)).isEqualTo(false)
        history.reset()
        assertThat(history.get(orange).isEmpty()).isEqualTo(true)
    }

    private fun catalogWords() = javaClass.getResourceAsStream(WORDS_RESOURCE)!!
        .bufferedReader(Charsets.UTF_8)
        .use { reader ->
            Json.parseToJsonElement(reader.readText())
                .jsonObject
                .getValue("words")
                .jsonArray
                .map { it.jsonObject }
        }

    private companion object {
        const val WORDS_RESOURCE = "/data/yet-another-minecraft-bingo/ddi/words_v1.json"
    }
}
