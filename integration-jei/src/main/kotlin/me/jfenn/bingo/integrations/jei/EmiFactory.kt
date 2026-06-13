package me.jfenn.bingo.integrations.jei

import me.jfenn.bingo.client.integrations.jei.IJeiApi
import me.jfenn.bingo.client.integrations.jei.IJeiApiFactory
import me.jfenn.bingo.platform.IModEnvironment

class EmiFactory : IJeiApiFactory {
    override fun create(environment: IModEnvironment): IJeiApi? {
        return if (environment.isModLoaded("emi"))
            EmiApi()
        else null
    }
}