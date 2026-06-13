package me.jfenn.bingo.integrations.placeholders

import me.jfenn.bingo.platform.IModEnvironment

interface ITextPlaceholdersApiFactory {
    fun create(environment: IModEnvironment): ITextPlaceholdersApi
}