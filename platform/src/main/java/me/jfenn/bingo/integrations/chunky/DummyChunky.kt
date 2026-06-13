package me.jfenn.bingo.integrations.chunky

import org.koin.core.scope.Scope

object DummyChunky : IChunkyApi {
    override fun startPregen() {}
    override fun cancelTasks() {}

    object Factory : IChunkyApiFactory {
        override fun create(scope: Scope): IChunkyApi = DummyChunky
    }
}