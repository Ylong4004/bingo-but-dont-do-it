package me.jfenn.bingo.common.config

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.serializer
import me.jfenn.bingo.common.MOD_ID_BINGO
import me.jfenn.bingo.common.utils.json
import me.jfenn.bingo.platform.IModEnvironment
import me.jfenn.bingo.platform.config.IConfigManager
import org.apache.commons.io.IOUtils
import org.slf4j.LoggerFactory
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.StandardOpenOption
import java.time.Instant
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import kotlin.reflect.KType
import kotlin.reflect.typeOf

class ConfigManager(
    environment: IModEnvironment,
) : IConfigManager {

    private val log = LoggerFactory.getLogger("bingo")
    override val configDir = environment.configDir

    override fun inputStream(path: String): InputStream {
        val filePath = configDir.resolve(path)
        return Files.newInputStream(filePath)
    }

    override fun outputStream(path: String): OutputStream {
        val filePath = configDir.resolve(path)
        if (!Files.exists(filePath)) {
            filePath.parent.toFile().mkdirs()
        }
        return Files.newOutputStream(filePath)
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun <T: Any> read(type: KType, file: String): T {
        @Suppress("UNCHECKED_CAST")
        val serializer = serializer(type) as KSerializer<T>
        return try {
            inputStream(file).use { json.decodeFromStream(serializer, it) }
        } catch (e: NoSuchFileException) {
            null
        } catch (e: Exception) {
            log.error("Error reading $file", e)
            null
        } ?: run {
            json.decodeFromString(serializer, "{}").also { write(type, file, it) }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun <T: Any> write(type: KType, file: String, config: T) {
        @Suppress("UNCHECKED_CAST")
        val serializer = serializer(type) as KSerializer<T>
        outputStream(file).use {
            json.encodeToStream(serializer, config, it)
        }
    }

    override fun readLastModified(fileName: String): Instant {
        val file = configDir.resolve("$MOD_ID_BINGO/$fileName").toFile()
        return if (file.exists()) {
            Instant.ofEpochMilli(file.lastModified())
        } else {
            Instant.MIN
        }
    }

    class ConfigDelegate<T: Any>(
        private val configManager: IConfigManager,
        private val type: KType,
        private val file: String,
    ) : ReadWriteProperty<Any, T> {
        var value: T? = null

        override fun getValue(thisRef: Any, property: KProperty<*>): T {
            return value ?: configManager.read<T>(type, file).also { value = it }
        }

        override fun setValue(thisRef: Any, property: KProperty<*>, value: T) {
            this.value = value.also { configManager.write(type, file, value) }
        }
    }
}

/**
 * Obtains a resource InputStream from the config directory, or
 * creates it from the java resources if it doesn't exist
 */
fun IConfigManager.readStream(
    file: String,
    shouldWriteDefault: Boolean = true,
    default: () -> InputStream = {
        javaClass.getResourceAsStream("/$MOD_ID_BINGO/$file")!!
    },
): InputStream {
    val filePath = configDir.resolve("$MOD_ID_BINGO/$file")
    if (!Files.exists(filePath)) {
        if (!shouldWriteDefault)
            return default()

        filePath.parent.toFile().mkdirs()

        // write the default resource file to the config dir
        Files.newOutputStream(filePath, StandardOpenOption.CREATE).use { output ->
            default().use { input -> IOUtils.copy(input, output) }
        }
    }

    // return the config dir file content
    return FileInputStream(filePath.toFile())
}

inline fun <reified T: Any> IConfigManager.config(file: String) =
    ConfigManager.ConfigDelegate<T>(this, typeOf<T>(), file)
