package me.jfenn.bingo.common.config

import kotlinx.serialization.Serializable
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class PlayerConfig(
    val players: MutableMap<String, PlayerSettings> = ConcurrentHashMap()
) {

    operator fun get(uuid: UUID): PlayerSettings {
        return players[uuid.toString()] ?: PlayerSettings()
    }

}
