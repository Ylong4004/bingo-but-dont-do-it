package me.jfenn.bingo.integrations.voice

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.hasSize
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import org.junit.jupiter.api.Test

class VoiceKeywordTextTest {
    @Test
    fun `normalizer folds width case whitespace and punctuation`() {
        assertThat(VoiceKeywordNormalizer.normalize(" ＢＩＮＧＯ！ ")).isEqualTo("bingo")
        assertThat(VoiceKeywordNormalizer.normalize("等 一下，快！")).isEqualTo("等一下快")
        assertThat(VoiceKeywordNormalizer.normalize("𠮷 野 家")).isEqualTo("𠮷野家")
    }

    @Test
    fun `grammar preserves full subject IDs while exposing spoken phrases`() {
        val grammar = VoiceKeywordGrammar.fromSubjects(
            setOf("voice:等一下", "voice:等等", "voice:ＧＯ HOME"),
        )

        assertThat(grammar).isNotNull()
        assertThat(grammar!!.subjectByNormalizedPhrase["等一下"]).isEqualTo("voice:等一下")
        assertThat(grammar.subjectByNormalizedPhrase["gohome"]).isEqualTo("voice:ＧＯ HOME")
    }

    @Test
    fun `grammar includes raw and character-tokenized Chinese phrases`() {
        val grammar = VoiceKeywordGrammar.fromSubjects(
            setOf("voice:帮我", "voice:苦力怕", "voice:bingo棋盘"),
        )!!

        assertThat(grammar.grammarJson).contains("\"帮我\"")
        assertThat(grammar.grammarJson).contains("\"帮 我\"")
        assertThat(grammar.grammarJson).contains("\"苦力 怕\"")
        assertThat(grammar.grammarJson).contains("\"苦 力 怕\"")
        assertThat(grammar.grammarJson).contains("\"bingo 棋盘\"")
        assertThat(grammar.grammarJson).contains("\"bingo 棋 盘\"")
    }

    @Test
    fun `supplementary Han characters are kept as complete custom tokens`() {
        val grammar = VoiceKeywordGrammar.fromSubjects(setOf("voice:𠮷野家"))!!

        assertThat(grammar.subjectByNormalizedPhrase["𠮷野家"])
            .isEqualTo("voice:𠮷野家")
        assertThat(grammar.grammarJson).contains("\"𠮷 野 家\"")
    }

    @Test
    fun `out of vocabulary phrase follows its constrained Chinese syllable path`() {
        val grammar = VoiceKeywordGrammar.fromSubjects(setOf("voice:阴不阴"))!!

        assertThat(grammar.grammarJson).contains("\"阴不阴\"")
        assertThat(grammar.grammarJson).contains("\"阴 不 阴\"")
        val match = VoiceKeywordResultMatcher.matchFinalResult(
            """{"result":[{"conf":1.0,"word":"阴"},{"conf":1.0,"word":"不"},{"conf":1.0,"word":"阴"}],"text":"阴 不 阴"}""",
            grammar,
        )

        assertThat(match?.subjectId).isEqualTo("voice:阴不阴")
    }

    @Test
    fun `every built in alias and runtime custom subject receives a pronunciation path`() {
        val subjects = (1..VoiceKeywordGrammar.MAX_SUBJECTS)
            .mapTo(linkedSetOf()) { index -> "voice:局内自定义词$index" }
        val grammar = VoiceKeywordGrammar.fromSubjects(subjects)!!

        (1..VoiceKeywordGrammar.MAX_SUBJECTS).forEach { index ->
            assertThat(grammar.grammarJson).contains("\"局 内 自 定 义 词 $index\"")
        }
    }

    @Test
    fun `segmentation variants are bounded for long custom keywords`() {
        assertThat(VoiceKeywordNormalizer.grammarPhrases("甲乙丙丁戊己庚")).hasSize(64)
        assertThat(VoiceKeywordNormalizer.grammarPhrases("甲乙丙丁戊己庚辛")).hasSize(2)
        val multipleLongRuns = VoiceKeywordNormalizer.grammarPhrases(
            "甲乙丙丁戊己庚 辛壬癸子丑寅卯",
        )
        assertThat(multipleLongRuns).hasSize(64)
        assertThat("甲 乙 丙 丁 戊 己 庚 辛 壬 癸 子 丑 寅 卯" in multipleLongRuns)
            .isEqualTo(true)
    }

    @Test
    fun `repeated interjections tolerate Vosk collapse and duplication`() {
        val grammar = VoiceKeywordGrammar.fromSubjects(setOf("voice:啊啊啊"))!!
        val duplicated = VoiceKeywordResultMatcher.matchFinalResult(
            """{"result":[{"conf":0.72,"word":"啊啊啊"},{"conf":0.81,"word":"啊啊啊"}],"text":"啊啊啊 啊啊啊"}""",
            grammar,
        )
        val collapsedAroundUnknown = VoiceKeywordResultMatcher.matchFinalResult(
            """{"result":[{"conf":0.89,"word":"啊"},{"conf":0.70,"word":"[unk]"},{"conf":0.92,"word":"啊"}],"text":"啊 [unk] 啊"}""",
            grammar,
        )

        assertThat(duplicated?.subjectId).isEqualTo("voice:啊啊啊")
        assertThat(collapsedAroundUnknown?.subjectId).isEqualTo("voice:啊啊啊")
    }

    @Test
    fun `ordinary phrases still require every character`() {
        val grammar = VoiceKeywordGrammar.fromSubjects(setOf("voice:帮我"))!!

        assertThat(
            VoiceKeywordResultMatcher.matchFinalResult(
                """{"result":[{"conf":0.95,"word":"我"}],"text":"我"}""",
                grammar,
            )
        ).isNull()
    }

    @Test
    fun `only confident final words match and unknown spans are ignored`() {
        val grammar = VoiceKeywordGrammar.fromSubjects(setOf("voice:等一下"))!!
        val result = """
            {
              "result": [
                {"conf": 0.15, "word": "[unk]"},
                {"conf": 0.91, "word": "等"},
                {"conf": 0.87, "word": "一下"}
              ],
              "text": "[unk] 等 一下"
            }
        """.trimIndent()

        val match = VoiceKeywordResultMatcher.matchFinalResult(result, grammar)
        assertThat(match).isNotNull()
        assertThat(match!!.subjectId).isEqualTo("voice:等一下")
    }

    @Test
    fun `partial or low-confidence output never matches`() {
        val grammar = VoiceKeywordGrammar.fromSubjects(setOf("voice:钻石"))!!
        assertThat(
            VoiceKeywordResultMatcher.matchFinalResult("""{"partial":"钻石"}""", grammar)
        ).isNull()
        assertThat(
            VoiceKeywordResultMatcher.matchFinalResult(
                """{"result":[{"conf":0.29,"word":"钻石"}],"text":"钻石"}""",
                grammar,
            )
        ).isNull()
    }

    @Test
    fun realisticChineseSmallModelConfidenceMatchesConstrainedGrammar() {
        val grammar = VoiceKeywordGrammar.fromSubjects(setOf("voice:等一下"))!!
        val match = VoiceKeywordResultMatcher.matchFinalResult(
            """
                {
                  "result": [
                    {"conf": 0.58, "word": "等"},
                    {"conf": 0.62, "word": "一下"}
                  ],
                  "text": "等 一下"
                }
            """.trimIndent(),
            grammar,
        )

        assertThat(match).isNotNull()
        assertThat(match!!.subjectId).isEqualTo("voice:等一下")
    }

    @Test
    fun exactFinalTextIsAcceptedWhenNativeResultOmitsWordDetails() {
        val grammar = VoiceKeywordGrammar.fromSubjects(setOf("voice:钻石"))!!
        val evaluation = VoiceKeywordResultMatcher.evaluateFinalResult(
            """{"text":"钻 石"}""",
            grammar,
        )

        assertThat(evaluation is VoiceKeywordResultEvaluation.Matched).isEqualTo(true)
        assertThat(
            (evaluation as VoiceKeywordResultEvaluation.Matched).usedTextFallback
        ).isEqualTo(true)
        assertThat(evaluation.match.subjectId).isEqualTo("voice:钻石")
    }
}
