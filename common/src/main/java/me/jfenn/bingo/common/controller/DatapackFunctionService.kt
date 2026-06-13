package me.jfenn.bingo.common.controller

import me.jfenn.bingo.common.MOD_ID_BINGO
import me.jfenn.bingo.common.config.TrackedFileService
import me.jfenn.bingo.common.data.ScopedData
import me.jfenn.bingo.common.scope.BingoComponent
import me.jfenn.bingo.common.state.GameState
import me.jfenn.bingo.platform.ICommandRunner
import me.jfenn.bingo.platform.IModEnvironment
import net.minecraft.server.MinecraftServer
import org.slf4j.Logger

internal class DatapackFunctionService(
    private val log: Logger,
    private val server: MinecraftServer,
    private val commandRunner: ICommandRunner,
    private val scopedData: ScopedData,
): BingoComponent() {

    companion object {
        val SUPPORTED_EVENTS = arrayOf(
            GameState.PREGAME,
            GameState.STARTING,
            GameState.COUNTDOWN,
            GameState.PLAYING,
            GameState.POSTGAME
        )

        fun filePath(state: GameState) = "$MOD_ID_BINGO/commands/on_${state.name.lowercase()}.mcfunction"
    }

    internal class Loader(
        private val trackedFileService: TrackedFileService,
        private val environment: IModEnvironment,
    ) {
        private fun loadCommandFile(state: GameState): String {
            val pathStr = filePath(state)
            val path = environment.configDir.resolve(pathStr)
            val resource = this::class.java
                .getResourceAsStream("/$pathStr")
                ?.use { stream -> stream.bufferedReader().use { it.readText() } }
                .orEmpty()

            return trackedFileService.readTextFileOrResource(
                path = path,
                resource = resource,
            ).config.orEmpty()
        }

        fun loadCommands(): Map<GameState, String> {
            return SUPPORTED_EVENTS.associateWith { loadCommandFile(it) }
        }
    }

    private fun runCommand(command: String) {
        try {
            log.debug("Running command '${command}'")
            commandRunner.runSilentCommand(server, command)
        } catch (e: Throwable) {
            log.error("Unable to run command '${command}'", e)
        }
    }

    /**
     * run "#yet-another-minecraft-bingo:on_playing" functions when the game state is changed
     */
    fun runStateChange(state: GameState) {
        if (state !in SUPPORTED_EVENTS)
            return

        runCommand("function #${MOD_ID_BINGO}:on_${state.name.lowercase()}")

        scopedData.commandFiles[state]
            ?.split(Regex("\\R"))
            ?.map { it.removePrefix("/").trim() }
            ?.filter { it.isNotBlank() && !it.startsWith("#") }
            ?.forEach { runCommand(it) }
    }
}