package me.jfenn.bingo.common.spawn

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class SpawnKit(
    var items: List<JsonElement>,
) {
    companion object {
        val EMPTY get() = SpawnKit(emptyList())
    }
}

class SpawnKits(
    var playerKit: SpawnKit = SpawnKit.EMPTY,
    var teamKit: SpawnKit = SpawnKit.EMPTY,
)