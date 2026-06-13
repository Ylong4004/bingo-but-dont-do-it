package me.jfenn.bingo.platform.config

import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Path
import java.time.Instant
import kotlin.reflect.KType

interface IConfigManager {
    val configDir: Path

    fun inputStream(path: String): InputStream
    fun outputStream(path: String): OutputStream

    /**
     * Returns the last modified timestamp if the file exists on disk
     * Otherwise, returns DISTANT_PAST
     */
    fun readLastModified(fileName: String): Instant
    fun <T: Any> read(type: KType, file: String): T
    fun <T: Any> write(type: KType, file: String, config: T)
}

inline fun <reified T: Any> IConfigManager.read(file: String): T =
    read(kotlin.reflect.typeOf<T>(), file)

inline fun <reified T: Any> IConfigManager.write(file: String, config: T) =
    write(kotlin.reflect.typeOf<T>(), file, config)
