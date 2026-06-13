package me.jfenn.bingo.platform.scope

import net.minecraft.server.MinecraftServer
import org.koin.core.KoinApplication

object BingoKoin {

    lateinit var koinApp: KoinApplication

    val scopeManager by lazy { koinApp.koin.get<IScopeManager>() }

    fun getScope(server: MinecraftServer?) = scopeManager.getScope(server)

}