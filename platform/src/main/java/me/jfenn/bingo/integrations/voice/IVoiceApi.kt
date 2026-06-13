package me.jfenn.bingo.integrations.voice

import me.jfenn.bingo.platform.IPlayerHandle

interface IVoiceApi {
    fun isInstalled(): Boolean
    fun createGroup(settings: VoiceGroupSettings): IGroupHandle?
}

interface IGroupHandle {
    fun addPlayer(player: IPlayerHandle)
    fun removePlayer(player: IPlayerHandle)
    fun close()
}
