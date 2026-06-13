package me.jfenn.bingo.common.text

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.decodeFromStream
import me.jfenn.bingo.common.MOD_ID_BINGO
import me.jfenn.bingo.common.config.TrackedFileService
import me.jfenn.bingo.common.data.ScopedData
import me.jfenn.bingo.common.utils.json
import me.jfenn.bingo.platform.IJsonSerializers
import me.jfenn.bingo.platform.IModEnvironment
import me.jfenn.bingo.platform.text.IText
import me.jfenn.bingo.platform.text.ITextSerialized
import net.minecraft.resource.ResourceManager
import net.minecraft.util.Identifier
import org.slf4j.Logger
import kotlin.jvm.optionals.getOrNull

internal class MessageService(
    private val placeholderService: PlaceholderService,
    private val serializers: IJsonSerializers,
    private val scopedData: ScopedData,
) {

    enum class MessageType {
        GAME_START,
        GAME_END,
        SCOREBOARD,
    }

    internal class Loader(
        private val trackedFileService: TrackedFileService,
        private val environment: IModEnvironment,
        private val serializers: IJsonSerializers,
        private val log: Logger,
    ) {
        private fun filePath(type: MessageType) = "$MOD_ID_BINGO/messages/${type.name.lowercase()}.json"

        private fun loadMessage(
            manager: ResourceManager,
            type: MessageType
        ): MessageData {
            val pathStr = filePath(type)
            val path = environment.configDir.resolve(pathStr)

            // Attempt to retrieve from datapack; otherwise, fall back to jar resources
            val resourceStream = manager.getResource(Identifier.of(MOD_ID_BINGO, pathStr))
                .getOrNull()
                ?.inputStream
                ?: this::class.java.getResourceAsStream("/data/$MOD_ID_BINGO/$pathStr")

            val resource = resourceStream
                .use {
                    @OptIn(ExperimentalSerializationApi::class)
                    json.decodeFromStream<MessageData>(it)
                }

            val result = trackedFileService.readFileOrResource(
                path = path,
                resource = resource,
            ).config ?: MessageData(emptyList())

            try {
                result.lines.forEach { serializers.json.decodeFromJsonElement<ITextSerialized>(it) }
            } catch (e: Throwable) {
                log.error("[messages/${type.name.lowercase()}.json] Could not parse text:", e)
                return MessageData(emptyList())
            }

            return result
        }

        fun loadMessages(manager: ResourceManager): Map<MessageType, MessageData> {
            return MessageType.entries.associateWith { loadMessage(manager, it) }
        }
    }

    fun getLines(
        type: MessageType,
        replacements: Map<String, List<IText>>
    ): List<IText> {
        val message = scopedData.messages[type] ?: MessageData(emptyList())

        val lines = message.lines
            .flatMap { line ->
                line
                    .takeIf { it is JsonPrimitive && it.isString }
                    ?.let { replacements[(it as? JsonPrimitive)?.content] }
                    ?: listOf(serializers.json.decodeFromJsonElement<ITextSerialized>(line))
            }

        return lines.map { line ->
            placeholderService.parseText(line)
        }
    }

}