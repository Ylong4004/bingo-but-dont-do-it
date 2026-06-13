package me.jfenn.bingo.integrations.voice

import me.jfenn.bingo.platform.IModEnvironment

class VoiceApiFactory : IVoiceApiFactory {
    override fun create(environment: IModEnvironment): IVoiceApi? {
        return when {
            environment.isModLoaded("voicechat") -> SimpleVoiceApi()
            else -> null
        }
    }
}
