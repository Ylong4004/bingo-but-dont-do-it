package me.jfenn.bingo.impl

import me.jfenn.bingo.platform.IExecutors
import net.minecraft.server.MinecraftServer
import net.minecraft.server.ServerTask
import net.minecraft.util.Util
import java.util.concurrent.ExecutorService

object Executors : IExecutors {
    override val main: ExecutorService = Util.getMainWorkerExecutor()
    override val io: ExecutorService = Util.getIoWorkerExecutor()

    override fun createServerTaskExecutor(server: MinecraftServer): IExecutors.IServerTaskExecutor {
        return ServerTaskExecutor(server)
    }
}

class ServerTaskExecutor(
    private val server: MinecraftServer
) : IExecutors.IServerTaskExecutor {
    override fun execute(runnable: Runnable) {
        // Use (ticks - 3) to run the task immediately (otherwise there's a 3 tick delay)
        server.send(ServerTask(server.ticks - 3, runnable))
    }
}
