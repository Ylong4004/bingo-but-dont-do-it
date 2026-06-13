package me.jfenn.bingo.common.datapack

import me.jfenn.bingo.platform.IModEnvironment
import kotlin.io.path.readText

class ServerProps(
    environment: IModEnvironment,
) {

    private val gameDir = environment.gameDir

    private val serverProperties = gameDir.resolve("server.properties")

    private fun readServerProperties() = try {
        serverProperties.readText().split(Regex("\\R"))
    } catch (e: Throwable) {
        null
    }

    // If server.properties exists, find level-name=, otherwise default to "world"
    val levelName = readServerProperties()
        ?.find { it.startsWith("level-name=") }
        ?.removePrefix("level-name=")
        ?.ifEmpty { "." }
        ?: "world"
}