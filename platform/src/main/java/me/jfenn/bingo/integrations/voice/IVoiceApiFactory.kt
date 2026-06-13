package me.jfenn.bingo.integrations.voice

import me.jfenn.bingo.platform.IModEnvironment

interface IVoiceApiFactory {
    fun create(environment: IModEnvironment): IVoiceApi?
}