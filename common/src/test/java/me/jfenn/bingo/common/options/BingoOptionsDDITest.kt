package me.jfenn.bingo.common.options

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotEqualTo
import assertk.assertions.isTrue
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import me.jfenn.bingo.common.utils.json
import org.junit.jupiter.api.Test

class BingoOptionsDDITest {

    @Test
    fun `old options without an objective mode keep individual behavior`() {
        val options = json.decodeFromString<BingoOptions>("{}")

        assertThat(options.ddiObjectiveMode).isEqualTo(DDIObjectiveMode.INDIVIDUAL)
        assertThat(options.ddiMaxHearts).isEqualTo(3)
        assertThat(options.ddiWordsPerObjective).isEqualTo(2)
        assertThat(options.ddiMultiHitPolicy).isEqualTo(DDIMultiHitPolicy.ALL_MATCHED)
        assertThat(options.ddiSpecialEventsEnabled).isFalse()
        assertThat(options.ddiSpecialEventIntervalSeconds).isEqualTo(300)
        assertThat(options.ddiSpecialEventTypes).isEqualTo(DDISpecialEventType.BALANCED)
        assertThat(options.ddiVoiceKeywordsEnabled).isFalse()
        assertThat(options.ddiVoiceCustomKeywords).isEqualTo(emptyList())
    }

    @Test
    fun `DDI objective modes have different difficulty hashes`() {
        val individual = BingoOptions(
            enableDDI = true,
            ddiObjectiveMode = DDIObjectiveMode.INDIVIDUAL,
        )
        val teamShared = individual.copy(ddiObjectiveMode = DDIObjectiveMode.TEAM_SHARED)

        assertThat(teamShared.getShaHash()).isNotEqualTo(individual.getShaHash())
    }

    @Test
    fun `disabled DDI keeps its existing hash independent of objective mode`() {
        val individual = BingoOptions(ddiObjectiveMode = DDIObjectiveMode.INDIVIDUAL)
        val teamShared = individual.copy(ddiObjectiveMode = DDIObjectiveMode.TEAM_SHARED)

        assertThat(teamShared.getShaHash()).isEqualTo(individual.getShaHash())
    }

    @Test
    fun `DDI heart and timer values have unambiguous hash boundaries`() {
        val oneHeart = BingoOptions(
            enableDDI = true,
            ddiMaxHearts = 1,
            ddiWordTimerSeconds = 160,
        )
        val elevenHearts = oneHeart.copy(
            ddiMaxHearts = 11,
            ddiWordTimerSeconds = 60,
        )

        assertThat(oneHeart.getShaHash()).isNotEqualTo(elevenHearts.getShaHash())
    }

    @Test
    fun `enabled DDI rejects out of range persisted values`() {
        assertThat(BingoOptions(enableDDI = true, ddiMaxHearts = 0).isValid()).isFalse()
        assertThat(BingoOptions(enableDDI = true, ddiMaxHearts = 21).isValid()).isFalse()
        assertThat(BingoOptions(enableDDI = true, ddiWordTimerSeconds = 9).isValid()).isFalse()
        assertThat(BingoOptions(enableDDI = true, ddiWordTimerSeconds = 601).isValid()).isFalse()
        assertThat(BingoOptions(enableDDI = true, ddiWordsPerObjective = 0).isValid()).isFalse()
        assertThat(BingoOptions(enableDDI = true, ddiWordsPerObjective = 6).isValid()).isFalse()
    }

    @Test
    fun `disabled DDI does not invalidate an old saved configuration`() {
        assertThat(
            BingoOptions(
                enableDDI = false,
                ddiMaxHearts = 0,
                ddiWordTimerSeconds = 0,
            ).isValid()
        ).isTrue()
    }

    @Test
    fun `disabled DDI extensions preserve the previous DDI hash`() {
        val oldDDI = BingoOptions(enableDDI = true)
        val ignoredExtensionValues = oldDDI.copy(
            ddiSpecialEventsEnabled = false,
            ddiSpecialEventIntervalSeconds = -1,
            ddiSpecialEventTypes = emptySet(),
            ddiVoiceKeywordsEnabled = false,
            ddiVoiceCustomKeywords = listOf(" invalid "),
        )

        assertThat(ignoredExtensionValues.getShaHash()).isEqualTo(oldDDI.getShaHash())
        assertThat(ignoredExtensionValues.isValid()).isTrue()
    }

    @Test
    fun `enabled special event hash is deterministic and includes its settings`() {
        val options = BingoOptions(
            enableDDI = true,
            ddiSpecialEventsEnabled = true,
            ddiSpecialEventIntervalSeconds = 180,
            ddiSpecialEventTypes = linkedSetOf(
                DDISpecialEventType.FOOD_RAIN,
                DDISpecialEventType.MONSTER_RAMPAGE,
            ),
        )
        val reordered = options.copy(
            ddiSpecialEventTypes = linkedSetOf(
                DDISpecialEventType.MONSTER_RAMPAGE,
                DDISpecialEventType.FOOD_RAIN,
            )
        )

        assertThat(reordered.getShaHash()).isEqualTo(options.getShaHash())
        assertThat(options.copy(ddiSpecialEventIntervalSeconds = 300).getShaHash())
            .isNotEqualTo(options.getShaHash())
        assertThat(options.copy(ddiSpecialEventTypes = setOf(DDISpecialEventType.FOOD_RAIN)).getShaHash())
            .isNotEqualTo(options.getShaHash())
    }

    @Test
    fun `enabled voice keyword hash is stable across ordering and equivalent case`() {
        val options = BingoOptions(
            enableDDI = true,
            ddiVoiceKeywordsEnabled = true,
            ddiVoiceCustomKeywords = listOf("Diamond", "下界传送门"),
        )
        val reordered = options.copy(
            ddiVoiceCustomKeywords = listOf("下界传送门", "diamond"),
        )

        assertThat(reordered.getShaHash()).isEqualTo(options.getShaHash())
        assertThat(options.copy(ddiVoiceCustomKeywords = listOf("Diamond")).getShaHash())
            .isNotEqualTo(options.getShaHash())
    }

    @Test
    fun `word catalog selection contributes to DDI hash independently of ordering`() {
        val options = BingoOptions(
            enableDDI = true,
            ddiDisabledWordCategories = setOf("voice", "craft"),
            ddiDisabledWordIds = setOf("craft_table_01", "voice_diamond"),
        )
        val reordered = options.copy(
            ddiDisabledWordCategories = setOf("craft", "voice"),
            ddiDisabledWordIds = setOf("voice_diamond", "craft_table_01"),
        )

        assertThat(reordered.getShaHash()).isEqualTo(options.getShaHash())
        assertThat(options.copy(ddiDisabledWordIds = setOf("voice_diamond")).getShaHash())
            .isNotEqualTo(options.getShaHash())
    }

    @Test
    fun `enabled special events require a valid interval and nonempty pool`() {
        val options = BingoOptions(enableDDI = true, ddiSpecialEventsEnabled = true)

        assertThat(options.copy(ddiSpecialEventIntervalSeconds = 29).isValid()).isFalse()
        assertThat(options.copy(ddiSpecialEventIntervalSeconds = 3601).isValid()).isFalse()
        assertThat(options.copy(ddiSpecialEventTypes = emptySet()).isValid()).isFalse()
        assertThat(options.copy(ddiSpecialEventIntervalSeconds = 30).isValid()).isTrue()
        assertThat(options.copy(ddiSpecialEventIntervalSeconds = 3600).isValid()).isTrue()
    }

    @Test
    fun `enabled voice keywords reject invalid duplicate and over-budget persisted lists`() {
        val options = BingoOptions(enableDDI = true, ddiVoiceKeywordsEnabled = true)

        assertThat(options.copy(ddiVoiceCustomKeywords = listOf(" x ")).isValid()).isFalse()
        assertThat(options.copy(ddiVoiceCustomKeywords = listOf("Diamond", "diamond")).isValid()).isFalse()
        assertThat(
            options.copy(
                ddiVoiceCustomKeywords = customKeywords(256)
            ).isValid()
        ).isTrue()
        assertThat(
            options.copy(
                ddiVoiceCustomKeywords = customKeywords(257)
            ).isValid()
        ).isFalse()
        assertThat(options.copy(ddiVoiceCustomKeywords = listOf("钻石", "nether portal")).isValid()).isTrue()
    }

    @Test
    fun `voice keyword normalization deduplicates equivalent entries`() {
        val normalized = DDIVoiceKeywordOptions.normalizeList(
            listOf("  Nether   Portal ", "nether portal", "下界传送门")
        )

        assertThat(normalized).isEqualTo(listOf("Nether Portal", "下界传送门"))
    }

    private fun customKeywords(count: Int): List<String> = (0 until count).map { index ->
        "word${index.toString().padStart(28, 'x')}"
    }

    @Test
    fun `new DDI settings survive JSON serialization`() {
        val options = BingoOptions(
            enableDDI = true,
            ddiSpecialEventsEnabled = true,
            ddiSpecialEventIntervalSeconds = 420,
            ddiSpecialEventTypes = setOf(
                DDISpecialEventType.TNT_RAIN,
                DDISpecialEventType.TRADE_MERCHANT,
            ),
            ddiVoiceKeywordsEnabled = true,
            ddiVoiceCustomKeywords = listOf("钻石", "nether portal"),
            ddiWordsPerObjective = 3,
            ddiMultiHitPolicy = DDIMultiHitPolicy.FIRST_MATCHED,
            ddiDisabledWordCategories = setOf("voice"),
            ddiDisabledWordIds = setOf("craft_table_01"),
        )

        val restored = json.decodeFromString<BingoOptions>(json.encodeToString(options))

        assertThat(restored.ddiSpecialEventsEnabled).isEqualTo(options.ddiSpecialEventsEnabled)
        assertThat(restored.ddiSpecialEventIntervalSeconds).isEqualTo(options.ddiSpecialEventIntervalSeconds)
        assertThat(restored.ddiSpecialEventTypes).isEqualTo(options.ddiSpecialEventTypes)
        assertThat(restored.ddiVoiceKeywordsEnabled).isEqualTo(options.ddiVoiceKeywordsEnabled)
        assertThat(restored.ddiVoiceCustomKeywords).isEqualTo(options.ddiVoiceCustomKeywords)
        assertThat(restored.ddiWordsPerObjective).isEqualTo(options.ddiWordsPerObjective)
        assertThat(restored.ddiMultiHitPolicy).isEqualTo(options.ddiMultiHitPolicy)
        assertThat(restored.ddiDisabledWordCategories).isEqualTo(options.ddiDisabledWordCategories)
        assertThat(restored.ddiDisabledWordIds).isEqualTo(options.ddiDisabledWordIds)
    }
}
