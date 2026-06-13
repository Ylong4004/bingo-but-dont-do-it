package me.jfenn.bingo.integrations.placeholders

import me.jfenn.bingo.platform.IModEnvironment

class TextPlaceholdersApiFactory : ITextPlaceholdersApiFactory {
    override fun create(environment: IModEnvironment): ITextPlaceholdersApi {
        return when {
            environment.isModLoaded("placeholder-api") -> TextPlaceholdersApi()
            else -> DummyPlaceholders
        }
    }
}