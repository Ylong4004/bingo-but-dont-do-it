package me.jfenn.bingo.client.common.sound

import me.jfenn.bingo.client.platform.IClientSoundManager
import me.jfenn.bingo.common.config.BingoConfig

internal class SoundService(
    private val config: BingoConfig,
    private val soundManager: IClientSoundManager,
    private val sounds: ClientSounds,
) {

    fun play(
        sound: ClientSounds.Key,
        volume: Float = 1f,
        pitch: Float = 1f,
        actualVolume: Float = volume * config.client.getSoundVolume(sound.name.lowercase()),
    ) {
        soundManager.play(
            sounds[sound],
            actualVolume,
            pitch
        )
    }

}