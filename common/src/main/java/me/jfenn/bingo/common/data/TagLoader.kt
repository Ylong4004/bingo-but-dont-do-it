package me.jfenn.bingo.common.data

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import me.jfenn.bingo.common.MOD_ID_BINGO
import me.jfenn.bingo.common.card.tag.TagData
import me.jfenn.bingo.common.config.TrackedFileService
import me.jfenn.bingo.common.utils.json
import me.jfenn.bingo.common.utils.jsonStrict
import me.jfenn.bingo.platform.IModEnvironment
import net.minecraft.resource.ResourceManager
import org.slf4j.Logger
import java.io.IOException

internal class TagLoader(
    private val trackedFileService: TrackedFileService,
    private val environment: IModEnvironment,
    private val log: Logger,
) {

    private fun readTagsFiles() : List<String> {
        return environment.configDir
            .resolve(TAGS_PATH)
            .toFile()
            .listFiles()
            .orEmpty()
            .filter { it.name.endsWith(FILE_SUFFIX) }
            .map { it.name.removeSuffix(FILE_SUFFIX) }
    }

    fun loadTags(
        manager: ResourceManager,
    ): Map<String, TagData> {
        log.info("[TagLoader] Reloading BINGO objective tags...")

        val newTags = mutableMapOf<String, TagData>()

        val lists = manager.findAllResources(TAGS_PATH) { it.path.endsWith(FILE_SUFFIX) }
        for ((id, files) in lists) {
            val name = id.path.substringAfterLast('/').removeSuffix(FILE_SUFFIX)
            val configs = files.mapNotNull { file ->
                try {
                    file.inputStream.use {
                        @OptIn(ExperimentalSerializationApi::class)
                        jsonStrict.decodeFromStream<TagData>(it)
                    }
                } catch (e: Throwable) {
                    log.error("[TagLoader] Error reading tag file $id: ${e.message}")
                    null
                }
            }

            if (configs.isEmpty()) continue
            val config = configs.reduce { acc, tag -> tag.plus(acc) }
            newTags[name] = (newTags[name] ?: TagData.EMPTY).plus(config)
                .also { it.shouldValidate = id.namespace != MOD_ID_BINGO }
        }

        for (name in (readTagsFiles() + newTags.keys).distinct()) {
            val resource = newTags[name]
            val (isModified, finalConfig) = trackedFileService.readFileOrResource(
                path = configFile(name),
                resource = resource,
            )

            if (finalConfig == null) continue
            finalConfig.shouldValidate = isModified || resource?.shouldValidate == true
            newTags[name] = finalConfig
        }

        return newTags
    }

    private fun configFile(tagName: String) =
        environment.configDir.resolve("$TAGS_PATH/$tagName${FILE_SUFFIX}")

    fun writeTag(name: String, tag: TagData) {
        val filePath = configFile(name)

        if (tag.isEmpty()) {
            filePath.toFile().delete()
            return
        }

        try {
            filePath.parent.toFile().mkdirs()
            filePath.toFile().writeText(json.encodeToString(tag))
        } catch (e: IOException) {
            log.error("Error writing to $filePath:", e)
        }
    }

    companion object {
        const val FILE_SUFFIX = ".json"
        const val TAGS_PATH = "$MOD_ID_BINGO/tags"
    }
}