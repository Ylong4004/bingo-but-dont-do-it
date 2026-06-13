package me.jfenn.bingo.integrations.voice

import kotlinx.serialization.Serializable

@Serializable
data class VoiceConfig(
    val enabled: Boolean = true,
    val useCombinedGroupForSingleplayerTeams: Boolean = true,
    val useCombinedGroupAlways: Boolean = false,
    val teamGroups: VoiceGroupSettings? = VoiceGroupSettings(name = "Bingo: %s Team"),
    val spectatorGroup: VoiceGroupSettings? = VoiceGroupSettings(name = "Bingo: Spectators", type = VoiceGroupType.NORMAL),
    val combinedGroup: VoiceGroupSettings? = VoiceGroupSettings(name = "Bingo"),
)
