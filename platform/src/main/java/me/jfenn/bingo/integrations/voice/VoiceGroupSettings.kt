package me.jfenn.bingo.integrations.voice

import kotlinx.serialization.Serializable

@Serializable
data class VoiceGroupSettings(
    val name: String = "Bingo",
    val type: VoiceGroupType = VoiceGroupType.OPEN,
    val password: String? = null,
    val hidden: Boolean = false,
)

@Serializable
enum class VoiceGroupType { OPEN, NORMAL, ISOLATED }
