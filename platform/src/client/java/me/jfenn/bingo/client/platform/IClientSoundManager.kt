package me.jfenn.bingo.client.platform

interface IClientSoundManager {
    fun createUnregistered(id: String) : IClientSoundHandle

    fun play(
        sound: IClientSoundHandle,
        volume: Float = 1f,
        pitch: Float = 1f
    )
}

interface IClientSoundHandle
