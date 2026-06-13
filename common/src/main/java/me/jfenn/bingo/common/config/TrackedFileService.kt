package me.jfenn.bingo.common.config

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.serializer
import me.jfenn.bingo.common.MOD_ID_BINGO
import me.jfenn.bingo.common.utils.json
import me.jfenn.bingo.platform.IModEnvironment
import org.slf4j.Logger
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.math.BigInteger
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant
import kotlin.io.path.deleteIfExists

internal class TrackedFileService(
    private val configService: ConfigService,
    private val environment: IModEnvironment,
    private val log: Logger,
) {

    private fun md5Of(data: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(data.toByteArray())
        val hexDigest = BigInteger(1, digest).toString(16)
        return hexDigest
    }

    data class Result<T>(
        val isModified: Boolean,
        val config: T?,
    )

    inline fun <reified T: Any> readFileOrResource(
        path: Path,
        resource: T?,
        jsonInstance: Json = json
    ): Result<T> {
        val serializer = serializer<T>()
        return readFileOrResource(
            path = path,
            resource = resource,
            serialize = { jsonInstance.encodeToString(serializer, it) },
            deserialize = {
                @OptIn(ExperimentalSerializationApi::class)
                jsonInstance.decodeFromStream(serializer, it)
            },
        )
    }

    fun readTextFileOrResource(path: Path, resource: String?): Result<String> {
        return readFileOrResource(
            path = path,
            resource = resource,
            serialize = { it },
            deserialize = { stream ->
                stream.bufferedReader().use { it.readText() }
            },
        )
    }

    fun <T: Any> readFileOrResource(
        path: Path,
        resource: T?,
        serialize: (T) -> String,
        deserialize: (InputStream) -> T,
    ): Result<T> {
        val name = environment.configDir.resolve(MOD_ID_BINGO).relativize(path).toString()
        val trackedFile: TrackedFile? = configService.files.filesMap[name]

        val file = path.toFile()
        val fileConfig = if (file.exists()) {
            try {
                FileInputStream(path.toFile()).use {
                    deserialize(it)
                }
            } catch (e: Throwable) {
                log.error("[$name] Error reading config:", e)
                return Result(true, null)
            }
        } else null

        val resourceJson = resource?.let { serialize(it) }
        val fileJson = fileConfig?.let { serialize(it) }

        if (resourceJson == null) {
            if (fileJson != null && md5Of(fileJson) == trackedFile?.md5) {
                // If the file hasn't been modified (was created from data, but no longer exists), remove it
                // - this may happen if a datapack providing the file was uninstalled
                log.warn("[$name] Resource no longer exists - removing from config folder!")
                path.deleteIfExists()
                configService.files = configService.files.minus(trackedFile)
                return Result(false, null)
            } else {
                // Otherwise, this is a new file that was created
                return Result(true, fileConfig)
            }
        }

        return if (
            fileConfig == null ||
            fileJson == null ||
            md5Of(fileJson) == trackedFile?.md5 ||
            md5Of(fileJson) == md5Of(resourceJson)
        ) {
            log.debug("[{}] Applying updates!", name)

            // If the file doesn't exist or has never been changed, replace it with the updated resource
            val newTrackedFile = TrackedFile(
                name = name,
                createdAt = trackedFile?.createdAt ?: Instant.now(),
                md5 = md5Of(resourceJson),
            )

            try {
                path.parent.toFile().mkdirs()
                path.toFile().writeText(resourceJson)
                configService.files = configService.files.plus(newTrackedFile)
            } catch (e: IOException) {
                log.error("Error writing to $path:", e)
            }
            Result(false, resource)
        } else {
            if (log.isDebugEnabled) {
                log.debug("[$name] Skipping - ${trackedFile?.md5} != ${md5Of(fileJson)} (original = ${md5Of(resourceJson)})")
            }
            // Otherwise, only use the config file
            Result(true, fileConfig)
        }
    }

}