package me.jfenn.bingo.common.scope

import me.jfenn.bingo.plugin.IBingoInternalPlugin
import org.koin.core.Koin
import org.koin.core.scope.Scope

class BingoPluginHolder(
    koin: Koin,
    private val plugins: List<IBingoInternalPlugin>
) {
    init {
        plugins.forEach { it.initialize(koin) }
    }

    fun onScopeStarted(scope: Scope) {
        plugins.forEach { it.onScopeStarted(scope) }
    }

    fun onScopeStopped(scope: Scope) {
        plugins.forEach { it.onScopeStopped(scope) }
    }

    fun close() {
        plugins.forEach { it.close() }
    }
}
