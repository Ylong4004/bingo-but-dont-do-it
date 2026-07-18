package me.jfenn.bingo.integrations.voice

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import org.junit.jupiter.api.Test

class VoiceKeywordTextTest {
    @Test
    fun `normalizer folds width case whitespace and punctuation`() {
        assertThat(VoiceKeywordNormalizer.normalize(" ＢＩＮＧＯ！ ")).isEqualTo("bingo")
        assertThat(VoiceKeywordNormalizer.normalize("等 一下，快！")).isEqualTo("等一下快")
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
