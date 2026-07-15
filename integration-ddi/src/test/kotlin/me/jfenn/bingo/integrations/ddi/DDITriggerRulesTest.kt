package me.jfenn.bingo.integrations.ddi

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class DDITriggerRulesTest {

    @Test
    fun `stationary rule compares against the fixed window anchor`() {
        assertThat(
            DDITriggerRules.isWithinStationaryAnchor(
                anchorX = 0.0,
                anchorY = 64.0,
                anchorZ = 0.0,
                currentX = 0.09,
                currentY = 64.0,
                currentZ = 0.0,
            )
        ).isTrue()
        assertThat(
            DDITriggerRules.isWithinStationaryAnchor(
                anchorX = 0.0,
                anchorY = 64.0,
                anchorZ = 0.0,
                currentX = 0.11,
                currentY = 64.0,
                currentZ = 0.0,
            )
        ).isFalse()
    }

    @Test
    fun `look rule handles yaw wrapping and cumulative rotation`() {
        assertThat(DDITriggerRules.angularDistance(359f, 1f)).isEqualTo(2f)
        assertThat(DDITriggerRules.isWithinLookAnchor(359f, 0f, 1f, 5f)).isTrue()
        assertThat(DDITriggerRules.isWithinLookAnchor(0f, 0f, 11f, 0f)).isFalse()
    }

    @Test
    fun `enemy damage requires final loss and a distinct opposing player`() {
        val attacker = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val victim = UUID.fromString("00000000-0000-0000-0000-000000000002")

        assertThat(DDITriggerRules.effectiveDamageLoss(0f, 2f)).isEqualTo(2f)
        assertThat(DDITriggerRules.effectiveDamageLoss(-1f, -1f)).isEqualTo(0f)
        assertThat(
            DDITriggerRules.isQualifyingEnemyPlayerDamage(2f, attacker, victim, areEnemies = true)
        ).isTrue()
        assertThat(
            DDITriggerRules.isQualifyingEnemyPlayerDamage(0f, attacker, victim, areEnemies = true)
        ).isFalse()
        assertThat(
            DDITriggerRules.isQualifyingEnemyPlayerDamage(2f, victim, victim, areEnemies = true)
        ).isFalse()
        assertThat(
            DDITriggerRules.isQualifyingEnemyPlayerDamage(2f, attacker, victim, areEnemies = false)
        ).isFalse()
    }
}
