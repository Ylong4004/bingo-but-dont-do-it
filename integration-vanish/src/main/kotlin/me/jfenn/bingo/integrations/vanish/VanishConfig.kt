package me.jfenn.bingo.integrations.vanish

import kotlinx.serialization.Serializable

@Serializable
data class VanishConfig(
    val enabled: Boolean = true,
)
