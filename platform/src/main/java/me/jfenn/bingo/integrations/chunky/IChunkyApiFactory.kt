package me.jfenn.bingo.integrations.chunky

import org.koin.core.scope.Scope

interface IChunkyApiFactory {
    fun create(scope: Scope): IChunkyApi
}