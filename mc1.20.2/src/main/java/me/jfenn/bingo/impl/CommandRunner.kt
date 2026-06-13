package me.jfenn.bingo.impl

import me.jfenn.bingo.platform.ICommandRunner
import net.minecraft.server.MinecraftServer
import net.minecraft.server.command.CommandOutput

class CommandRunner : ICommandRunner {
    override fun runSilentCommand(server: MinecraftServer, cmd: String) {
        server.commandManager.executeWithPrefix(server.commandSource.withOutput(CommandOutput.DUMMY), cmd)
    }
}