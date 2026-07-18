package me.jfenn.bingo.integrations.ddi

import java.util.UUID
import kotlin.math.abs

/** 独立保存的纯阈值规则，便于对边界语义进行单元测试。 */
internal object DDITriggerRules {
    private const val STATIONARY_RADIUS_SQUARED = 0.01 // 距窗口锚点 0.1 格
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

    /** 经减伤后仍生效的伤害，包括消耗的伤害吸收生命。 */
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
