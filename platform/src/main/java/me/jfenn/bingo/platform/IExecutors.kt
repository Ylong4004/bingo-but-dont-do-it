package me.jfenn.bingo.platform

import net.minecraft.server.MinecraftServer
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService

interface IExecutors {
    val main: ExecutorService
    val io: ExecutorService

    fun createServerTaskExecutor(server: MinecraftServer) : IServerTaskExecutor

    interface IServerTaskExecutor : Executor
}