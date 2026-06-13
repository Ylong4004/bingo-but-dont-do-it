package me.jfenn.bingo.integrations.jei

import me.jfenn.bingo.client.integrations.jei.IJeiApi
import me.jfenn.bingo.client.integrations.jei.IJeiApiFactory
import me.jfenn.bingo.platform.IModEnvironment

class ReiFactory : IJeiApiFactory {
    override fun create(environment: IModEnvironment): IJeiApi? {
        return if (environment.isModLoaded("roughlyenoughitems"))
            ReiApi()
        else null
    }
}