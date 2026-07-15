package me.jfenn.bingo.integrations.ddi

import java.util.UUID
import kotlin.math.abs

/** Pure threshold rules kept separate so edge semantics can be unit-tested. */
internal object DDITriggerRules {
    private const val STATIONARY_RADIUS_SQUARED = 0.01 // 0.1 blocks from the window anchor
    private const val LOOK_TOLERANCE_DEGREES = 10f

    fun isWithinStationaryAnchor(
        anchorX: Double,
        anchorY: Double,
        anchorZ: Double,
        currentX: Double,
        currentY: Double,
        currentZ: Double,
    ): Boolean {
        val dx = currentX - anchorX
        val dy = currentY - anchorY
        val dz = currentZ - anchorZ
        return dx * dx + dy * dy + dz * dz <= STATIONARY_RADIUS_SQUARED
    }

    fun isWithinLookAnchor(
        anchorYaw: Float,
        anchorPitch: Float,
        currentYaw: Float,
        currentPitch: Float,
    ): Boolean = angularDistance(anchorYaw, currentYaw) <= LOOK_TOLERANCE_DEGREES &&
        abs(currentPitch - anchorPitch) <= LOOK_TOLERANCE_DEGREES

    fun angularDistance(first: Float, second: Float): Float {
        val raw = abs(first - second) % 360f
        return if (raw > 180f) 360f - raw else raw
    }

    /** Damage that survived mitigation, including absorption hearts consumed. */
    fun effectiveDamageLoss(healthLost: Float, absorptionLost: Float): Float =
        healthLost.coerceAtLeast(0f) + absorptionLost.coerceAtLeast(0f)

    fun isQualifyingEnemyPlayerDamage(
        effectiveDamage: Float,
        attackerId: UUID?,
        victimId: UUID,
        areEnemies: Boolean,
    ): Boolean = effectiveDamage > 0f &&
        attackerId != null &&
        attackerId != victimId &&
        areEnemies
}
