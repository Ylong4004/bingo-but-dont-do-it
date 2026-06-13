package me.jfenn.bingo.common.data

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import me.jfenn.bingo.common.MOD_ID_BINGO
import me.jfenn.bingo.common.card.tierlist.TierListConfig
import me.jfenn.bingo.common.config.TrackedFileService
import me.jfenn.bingo.common.utils.json
import me.jfenn.bingo.common.utils.jsonStrict
import me.jfenn.bingo.platform.IModEnvironment
import net.minecraft.resource.ResourceManager
import org.slf4j.Logger
import java.io.IOException

internal class TierListLoader(
    private val trackedFileService: TrackedFileService,
    private val environment: IModEnvironment,
    private val log: Logger,
) {
    private fun readTierListFiles() : List<String> {
        return environment.configDir
            .resolve(TIERLISTS_PATH)
            .toFile()
            .listFiles()
            .orEmpty()
            .filter { it.name.endsWith(FILE_SUFFIX) }
            .map { it.name.removeSuffix(FILE_SUFFIX) }
    }

    fun loadTierLists(
        manager: ResourceManager
    ): Map<String, TierListConfig> {
        log.info("[TierListLoader] Reloading BINGO tier lists...")

        val newTierLists = mutableMapOf<String, TierListConfig>()

        val lists = manager.findAllResources(TIERLISTS_PATH) { it.path.endsWith(FILE_SUFFIX) }
        for ((id, files) in lists) {
            val name = id.path.substringAfterLast('/').removeSuffix(FILE_SUFFIX)

            val configs = files.mapNotNull { file ->
                try {
                    file.inputStream.use {
                        @OptIn(ExperimentalSerializationApi::class)
                        jsonStrict.decodeFromStream<TierListConfig>(it)
                    }
                } catch (e: Throwable) {
                    log.error("[TierListLoader] Error reading tier list $id: ${e.message}")
                    null
                }
            }

            if (configs.isEmpty()) continue
            val config = configs.reduce { acc, list -> list.combine(acc) }
            newTierLists[name] = (newTierLists[name] ?: TierListConfig.EMPTY).combine(config)
                .also { it.shouldValidate = id.namespace != MOD_ID_BINGO }
        }

        for (name in (readTierListFiles() + newTierLists.keys).distinct()) {
            val resource = newTierLists[name]
            val (isModified, finalConfig) = trackedFileService.readFileOrResource(
                path = configFile(name),
                resource = resource,
            )

            if (finalConfig == null) continue
            finalConfig.shouldValidate = isModified || resource?.shouldValidate == true
            newTierLists[name] = finalConfig
        }

        return newTierLists
    }

    private fun configFile(tierListName: String) =
        environment.configDir.resolve("$TIERLISTS_PATH/$tierListName$FILE_SUFFIX")

    fun writeTierList(name: String, tierList: TierListConfig) {
        val filePath = configFile(name)

        if (tierList.isEmpty()) {
            filePath.toFile().delete()
            return
        }

        try {
            filePath.parent.toFile().mkdirs()
            filePath.toFile().writeText(json.encodeToString(tierList))
        } catch (e: IOException) {
            log.error("Error writing to $filePath:", e)
        }
    }

    companion object {
        const val FILE_SUFFIX = ".tierlist.json"
        const val TIERLISTS_PATH = "$MOD_ID_BINGO/tierlists"
    }
}