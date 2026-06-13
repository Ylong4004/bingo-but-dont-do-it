package me.jfenn.bingo.common.spawn

import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import me.jfenn.bingo.common.data.ScopedData
import me.jfenn.bingo.platform.IJsonSerializers
import me.jfenn.bingo.platform.item.IItemStack
import me.jfenn.bingo.platform.item.IItemStackSerialized
import org.slf4j.Logger

internal class SpawnKitService(
    private val log: Logger,
    private val serializers: IJsonSerializers,
    private val spawnKitLoader: SpawnKitLoader,
    private val data: ScopedData,
) {
    private fun getItems(kit: SpawnKit): List<IItemStack> {
        return kit.items.mapNotNull {
            try {
                serializers.json.decodeFromJsonElement<IItemStackSerialized>(it)
            } catch (e: Throwable) {
                log.error("[SpawnKitService] Could not parse item stack: $it", e)
                null
            }
        }
    }

    fun getPlayerItems() = getItems(data.spawnKits.playerKit)

    fun getTeamItems() = getItems(data.spawnKits.teamKit)

    fun writePlayerItems(items: List<IItemStack>) {
        val spawnKit = SpawnKit(
            items.map { serializers.json.encodeToJsonElement<IItemStackSerialized>(it) }
        )
        data.spawnKits.playerKit = spawnKit
        spawnKitLoader.writeSpawnKit(SpawnKitLoader.KIT_PLAYER, spawnKit)
    }

    fun writeTeamItems(items: List<IItemStack>) {
        val spawnKit = SpawnKit(
            items.map { serializers.json.encodeToJsonElement<IItemStackSerialized>(it) }
        )
        data.spawnKits.teamKit = spawnKit
        spawnKitLoader.writeSpawnKit(SpawnKitLoader.KIT_TEAM, spawnKit)
    }
}