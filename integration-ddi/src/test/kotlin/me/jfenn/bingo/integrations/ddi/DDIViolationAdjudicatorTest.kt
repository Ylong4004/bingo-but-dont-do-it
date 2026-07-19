package me.jfenn.bingo.integrations.ddi

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

class DDIViolationAdjudicatorTest {

    private val adjudicator = DDIViolationAdjudicator()

    @Test
    fun `authoritative gameplay evidence settles automatically`() {
        assertThat(
            adjudicator.decide(
                DDIViolationEvidence(
                    source = DDIEvidenceSource.AUTHORITATIVE_GAMEPLAY,
                    exactTargetMatch = true,
                ),
            ),
        ).isEqualTo(DDIAdjudicationDecision.AUTOMATIC_PENALTY)
    }

    @Test
    fun `voice evidence needs exact target and measured confidence for automatic penalty`() {
        assertThat(
            adjudicator.decide(
                DDIViolationEvidence(
                    source = DDIEvidenceSource.VOICE_RECOGNITION,
                    exactTargetMatch = true,
                    confidence = 0.55,
                ),
            ),
        ).isEqualTo(DDIAdjudicationDecision.AUTOMATIC_PENALTY)
        assertThat(
            adjudicator.decide(
                DDIViolationEvidence(
                    source = DDIEvidenceSource.VOICE_RECOGNITION,
                    exactTargetMatch = false,
                    confidence = 1.0,
                ),
            ),
        ).isEqualTo(DDIAdjudicationDecision.REJECTED)
    }

    @Test
    fun `low or unavailable voice confidence never deducts a heart automatically`() {
        listOf(null, 0.0, 0.54).forEach { confidence ->
            assertThat(
                adjudicator.decide(
                    DDIViolationEvidence(
                        source = DDIEvidenceSource.VOICE_RECOGNITION,
                        exactTargetMatch = true,
                        confidence = confidence,
                    ),
                ),
            ).isEqualTo(DDIAdjudicationDecision.MANUAL_ACCUSATION_ONLY)
        }
    }

    @Test
    fun `invalid confidence is rejected rather than converted into a manual candidate`() {
        listOf(Double.NaN, Double.NEGATIVE_INFINITY, 1.01).forEach { confidence ->
            assertThat(
                adjudicator.decide(
                    DDIViolationEvidence(
                        source = DDIEvidenceSource.VOICE_RECOGNITION,
                        exactTargetMatch = true,
                        confidence = confidence,
                    ),
                ),
            ).isEqualTo(DDIAdjudicationDecision.REJECTED)
        }
    }
}
