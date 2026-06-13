package me.jfenn.bingo.common.scope

import me.jfenn.bingo.plugin.IBingoInternalPlugin
import org.koin.core.Koin

class BingoPluginHolder(
    koin: Koin,
    private val plugins: List<IBingoInternalPlugin>
) {
    init {
        plugins.forEach { it.initialize(koin) }
    }

    fun close() {
        plugins.forEach { it.close() }
    }
}