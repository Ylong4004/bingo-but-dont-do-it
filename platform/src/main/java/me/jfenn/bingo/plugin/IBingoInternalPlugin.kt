package me.jfenn.bingo.plugin

import org.koin.core.Koin
import org.koin.core.scope.Scope

interface IBingoInternalPlugin {
    fun initialize(koin: Koin) {}
    fun onScopeStarted(scope: Scope) {}
    fun onScopeStopped(scope: Scope) {}
    fun close() {}
}
