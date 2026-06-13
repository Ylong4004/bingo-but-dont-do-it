package me.jfenn.bingo.platform

import net.minecraft.server.MinecraftServer

interface IMinecraftServer {
    val server: MinecraftServer
    val isDedicated: Boolean
    val isSingleplayer: Boolean
}