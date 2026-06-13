package me.jfenn.bingo.common.config

import kotlinx.serialization.Serializable

@Serializable
class ChatConfig(
    val defaultToTeamChat: Boolean = true,
    val defaultToSpectatorChat: Boolean = true,
    val globalCommandAliases: List<String> = listOf("g", "global", "all"),
    val teamCommandAliases: List<String> = emptyList(),
)