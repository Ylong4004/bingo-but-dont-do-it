package me.jfenn.bingo.common.data

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import me.jfenn.bingo.common.MOD_ID_BINGO
import me.jfenn.bingo.common.team.BingoTeamKey
import me.jfenn.bingo.common.team.BingoTeamPreset
import me.jfenn.bingo.platform.IJsonSerializers
import net.minecraft.resource.ResourceManager
import org.slf4j.Logger

class TeamPresetLoader(
    private val serializers: IJsonSerializers,
    private val log: Logger,
) {

    fun loadTeamPresets(
        manager: ResourceManager,
    ): Map<BingoTeamKey, BingoTeamPreset> {
        log.info("[TeamPresetLoader] Reloading BINGO team presets...")

        val newTeamPresets = mutableMapOf<BingoTeamKey, BingoTeamPreset>()
        val json = serializers.jsonStrict

        val lists = manager.findResources(TEAM_PRESETS_PATH) { it.path.endsWith(FILE_SUFFIX) }
        for ((id, file) in lists) {
            val name = id.path.substringAfterLast('/').removeSuffix(FILE_SUFFIX)
            val config = try {
                file.inputStream.use {
                    @OptIn(ExperimentalSerializationApi::class)
                    json.decodeFromStream<BingoTeamPreset>(it)
                }
            } catch (e: Throwable) {
                log.error("[TeamPresetLoader] Error reading team preset $id: ${e.message}")
                continue
            }

            newTeamPresets[BingoTeamKey(name)] = config
        }

        return newTeamPresets
    }

    companion object {
        private const val FILE_SUFFIX = ".json"
        private const val TEAM_PRESETS_PATH = "$MOD_ID_BINGO/teams"
    }
}