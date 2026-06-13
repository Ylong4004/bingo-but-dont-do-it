package me.jfenn.bingo.client.platform

import net.minecraft.server.MinecraftServer

interface IWorldService {

    fun createBingoWorld()

    fun isBingoWorld(server: MinecraftServer): Boolean

    fun deleteSave(server: MinecraftServer)

}