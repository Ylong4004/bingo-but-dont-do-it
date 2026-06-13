package me.jfenn.bingo.integrations.chunky

import kotlinx.serialization.Serializable

@Serializable
class ChunkyConfig(
    val enabled: Boolean = true,
    val chunkyWorlds: Map<String, Int> = mapOf(
        "minecraft:overworld" to 0,
        "minecraft:the_nether" to 1000,
    )
)