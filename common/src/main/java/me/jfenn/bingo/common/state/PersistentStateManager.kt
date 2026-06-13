package me.jfenn.bingo.common.state

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.serializer
import me.jfenn.bingo.platform.IJsonSerializers
import me.jfenn.bingo.platform.IPersistentStateManager
import me.jfenn.bingo.platform.IPersistentStateType
import me.jfenn.bingo.platform.IServerWorldFactory
import me.jfenn.bingo.platform.event.IEventBus
import me.jfenn.bingo.platform.event.model.ServerEvent
import org.slf4j.Logger
import java.nio.file.Files
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.reflect.KType

@OptIn(ExperimentalSerializationApi::class)
class PersistentStateManager(
    val logger: Logger,
    serializers: IJsonSerializers,
    val serverWorldFactory: IServerWorldFactory,
    eventBus: IEventBus,
) : IPersistentStateManager {

    private val json = Json(serializers.json) {
        prettyPrint = true
    }
    private val registeredTypes = mutableMapOf<String, Type<*>>()
    private val writeOptions = arrayOf<OpenOption>(
        StandardOpenOption.SYNC,
        StandardOpenOption.WRITE,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING
    );

    override fun <T : Any> register(
        id: String,
        kType: KType,
        default: () -> T
    ): IPersistentStateType<T> {
        val path = serverWorldFactory.overworld.directory.resolve("data")
            .resolve("$id.json.gz")

        val type = Type(
            id,
            path,
            @Suppress("UNCHECKED_CAST") (serializer(kType) as KSerializer<T>),
            default()
        )
        registeredTypes[type.id] = type
        return type
    }

    override fun <T : Any> getFromWorld(
        type: IPersistentStateType<T>
    ): T {
        require(type is Type)
        val path = type.path
        if (Files.exists(path)) {
            try {
                logger.debug("Reading persistent state ${type.id}...")
                type.value = Files.newInputStream(path)
                    .buffered()
                    .let { GZIPInputStream(it) }
                    .use {
                        json.decodeFromStream(type.serializer, it)
                    }
            } catch (e: Throwable) {
                logger.error("Error decoding persistent state ${type.id}", e)
            }
        }

        return type.value
    }

    override fun <T : Any> put(
        type: IPersistentStateType<T>,
        value: T
    ) {
        require(type is Type)
        val path = type.path

        try {
            logger.debug("Writing persistent state ${type.id}...")
            Files.newOutputStream(path, *writeOptions)
                .buffered()
                .let { GZIPOutputStream(it) }
                .use {
                    json.encodeToStream(type.serializer, value, it)
                }
        } catch (e: Throwable) {
            logger.error("Error encoding persistent state ${type.id}", e)
        }

        type.value = value
        logger.debug("Writing persistent state ${type.id}... Done!")
    }

    private class Type<T: Any>(
        val id: String,
        val path: Path,
        val serializer: KSerializer<T>,
        var value: T,
    ) : IPersistentStateType<T>

    init {
        eventBus.register(ServerEvent.Saved) {
            registeredTypes.values.forEach {
                this.put<Any>(@Suppress("UNCHECKED_CAST") (it as Type<Any>), it.value)
            }
        }
    }
}