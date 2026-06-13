package me.jfenn.bingo.platform.scope

import net.minecraft.server.MinecraftServer
import org.koin.core.scope.Scope

interface IScopeManager {

    fun getScope(server: MinecraftServer?) : Scope?

}