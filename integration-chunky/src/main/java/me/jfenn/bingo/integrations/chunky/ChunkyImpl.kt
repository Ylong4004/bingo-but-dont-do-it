package me.jfenn.bingo.integrations.chunky

import me.jfenn.bingo.platform.ICommandRunner
import net.minecraft.server.MinecraftServer
import org.slf4j.Logger

class ChunkyImpl(
    private val server: MinecraftServer,
    private val config: ChunkyConfig,
    private val commandRunner: ICommandRunner,
    private val log: Logger,
) : IChunkyApi {

    init {
        log.info("Integrations: Chunky is installed!")
    }

    override fun startPregen() {
        try {
            config.chunkyWorlds.forEach { (world, size) ->
                if (size > 0) {
                    commandRunner.runSilentCommand(server, "chunky world $world")
                    commandRunner.runSilentCommand(server, "chunky radius $size")
                    commandRunner.runSilentCommand(server, "chunky start")
                }
            }
            commandRunner.runSilentCommand(server, "chunky continue")
        } catch (e: Throwable) {
            log.error("Error starting chunky pre-generation:", e)
        }
    }

    override fun cancelTasks() {
        try {
            commandRunner.runSilentCommand(server, "chunky cancel")
            commandRunner.runSilentCommand(server, "chunky confirm")
        } catch (e: Throwable) {
            log.error("Error cancelling chunky pre-generation:", e)
        }
    }

}