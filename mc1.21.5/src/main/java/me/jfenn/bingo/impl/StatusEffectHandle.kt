package me.jfenn.bingo.impl

import me.jfenn.bingo.platform.EffectType
import me.jfenn.bingo.platform.IStatusEffectHandle
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects

class StatusEffectHandle(
    val instance: StatusEffectInstance,
) : IStatusEffectHandle {
    override val type: EffectType = when (instance.effectType) {
        StatusEffects.NIGHT_VISION -> EffectType.NIGHT_VISION
        StatusEffects.SLOWNESS -> EffectType.SLOWNESS
        StatusEffects.JUMP_BOOST -> EffectType.JUMP_BOOST
        StatusEffects.INVISIBILITY -> EffectType.INVISIBILITY
        else -> EffectType.OTHER
    }
    override val duration: Int = instance.duration
}
