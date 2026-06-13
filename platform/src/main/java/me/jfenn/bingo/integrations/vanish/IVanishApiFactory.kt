package me.jfenn.bingo.integrations.vanish

import org.koin.core.scope.Scope

interface IVanishApiFactory {
    fun create(scope: Scope): IVanishApi?
}