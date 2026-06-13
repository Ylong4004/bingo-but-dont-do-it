package me.jfenn.bingo.common.spawn

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import me.jfenn.bingo.common.MOD_ID_BINGO
import me.jfenn.bingo.common.config.TrackedFileService
import me.jfenn.bingo.common.utils.json
import me.jfenn.bingo.platform.IModEnvironment
import net.minecraft.resource.ResourceManager
import org.slf4j.Logger
import java.io.IOException

internal class SpawnKitLoader(
    private val trackedFileService: TrackedFileService,
    private val environment: IModEnvironment,
    private val log: Logger,
) {

    private fun readSpawnKitFiles() : List<String> {
        return environment.configDir
            .resolve(TEAM_PRESETS_PATH)
            .toFile()
            .listFiles()
            .orEmpty()
            .filter { it.name.endsWith(FILE_SUFFIX) }
            .map { it.name.removeSuffix(FILE_SUFFIX) }
    }

    fun loadSpawnKits(
        manager: ResourceManager,
    ): SpawnKits {
        log.info("[SpawnKitLoader] Reloading BINGO spawn kits...")

        val newSpawnKits = mutableMapOf<String, SpawnKit>()

        val lists = manager.findResources(TEAM_PRESETS_PATH) { it.path.endsWith(FILE_SUFFIX) }
        for ((id, file) in lists) {
            val name = id.path.substringAfterLast('/').removeSuffix(FILE_SUFFIX)
            val config = try {
                file.inputStream.use {
                    @OptIn(ExperimentalSerializationApi::class)
                    json.decodeFromStream<SpawnKit>(it)
                }
            } catch (e: Throwable) {
                log.error("[SpawnKitLoader] Error reading spawn kit file $id: ${e.message}")
                continue
            }

            newSpawnKits[name] = config
        }

        for (name in (readSpawnKitFiles() + newSpawnKits.keys).distinct()) {
            val resource = newSpawnKits[name]
            val (_, finalConfig) = trackedFileService.readFileOrResource(
                path = configFile(name),
                resource = resource,
            )

            if (finalConfig == null) continue
            newSpawnKits[name] = finalConfig
        }

        return SpawnKits(
            playerKit = newSpawnKits[KIT_PLAYER] ?: SpawnKit.EMPTY,
            teamKit = newSpawnKits[KIT_TEAM] ?: SpawnKit.EMPTY,
        )
    }

    private fun configFile(tagName: String) =
        environment.configDir.resolve("$TEAM_PRESETS_PATH/$tagName${FILE_SUFFIX}")

    fun writeSpawnKit(name: String, kit: SpawnKit) {
        val filePath = configFile(name)

        try {
            filePath.parent.toFile().mkdirs()
            filePath.toFile().writeText(json.encodeToString(kit))
        } catch (e: IOException) {
            log.error("Error writing to $filePath:", e)
        }
    }

    companion object {
        private const val FILE_SUFFIX = ".json"
        private const val TEAM_PRESETS_PATH = "$MOD_ID_BINGO/kits"
        const val KIT_PLAYER = "playerkit"
        const val KIT_TEAM = "teamkit"
    }
}
