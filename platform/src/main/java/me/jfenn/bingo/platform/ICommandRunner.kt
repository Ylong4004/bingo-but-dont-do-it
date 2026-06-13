package me.jfenn.bingo.platform

import net.minecraft.server.MinecraftServer

interface ICommandRunner {
    fun runSilentCommand(server: MinecraftServer, cmd: String)
}