package me.jfenn.bingo.platform.player

import kotlinx.serialization.Serializable
import me.jfenn.bingo.platform.utils.UuidAsString

@Serializable
data class PlayerProfile(
    val uuid: UuidAsString,
    val name: String,
)
