package me.jfenn.bingo.client.integrations.jei

import me.jfenn.bingo.platform.IModEnvironment

interface IJeiApiFactory {
    fun create(environment: IModEnvironment): IJeiApi?
}