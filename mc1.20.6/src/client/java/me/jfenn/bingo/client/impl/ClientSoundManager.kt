package me.jfenn.bingo.client.impl

import me.jfenn.bingo.client.platform.IClientSoundHandle
import me.jfenn.bingo.client.platform.IClientSoundManager
import net.minecraft.client.MinecraftClient
import net.minecraft.client.sound.PositionedSoundInstance
import net.minecraft.client.sound.SoundInstance
import net.minecraft.sound.SoundCategory
import net.minecraft.util.Identifier

class ClientSoundManager : IClientSoundManager {

    override fun createUnregistered(id: String): IClientSoundHandle {
        return ClientSoundHandle(Identifier(id))
    }

    override fun play(sound: IClientSoundHandle, volume: Float, pitch: Float) {
        require(sound is ClientSoundHandle)

        val instance = PositionedSoundInstance(
            sound.id,
            SoundCategory.MASTER,
            volume,
            pitch,
            SoundInstance.createRandom(),
            false,
            0,
            SoundInstance.AttenuationType.NONE,
            0.0, 0.0, 0.0, true
        )
        MinecraftClient.getInstance().soundManager.stopSounds(sound.id, null)
        MinecraftClient.getInstance().soundManager.play(instance)
    }

    class ClientSoundHandle(
        val id: Identifier,
    ) : IClientSoundHandle
}