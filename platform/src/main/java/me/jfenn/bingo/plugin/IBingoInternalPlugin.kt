package me.jfenn.bingo.plugin

import org.koin.core.Koin

interface IBingoInternalPlugin {
    fun initialize(koin: Koin) {}
    fun close() {}
}
