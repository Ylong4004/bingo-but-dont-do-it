package me.jfenn.bingo.common

import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.IPlayerManager
import me.jfenn.bingo.platform.PlayerSoundCategory
import me.jfenn.bingo.platform.PlayerSoundEvent
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import org.joml.Vector3d

internal object Sounds {

    fun playButtonSound(playerManager: IPlayerManager, player: IPlayerHandle, fromPosition: Vector3d, fromWorld: ServerWorld, sound: PlayerSoundEvent = PlayerSoundEvent.BLOCK_WOODEN_BUTTON_CLICK_ON) {
        playerManager.playToAround(
            player.player,
            sound,
            PlayerSoundCategory.MAIN,
            0.8f,
            2f,
            Triple(fromPosition.x, fromPosition.y, fromPosition.z),
            fromWorld
        )
    }

    fun playGameCountdown(player: IPlayerHandle) {
        player.playSound(
            PlayerSoundEvent.BLOCK_NOTE_BLOCK_PLING,
            PlayerSoundCategory.RECORDS,
            1f, 1f
        )
    }

    fun playGameStarted(player: IPlayerHandle) {
        player.playSound(
            PlayerSoundEvent.BLOCK_NOTE_BLOCK_PLING,
            PlayerSoundCategory.RECORDS,
            1f, 2f
        )
    }

    fun playGameOver(player: IPlayerHandle) {
        player.playSound(
            PlayerSoundEvent.BLOCK_PORTAL_TRAVEL,
            PlayerSoundCategory.RECORDS,
            0.3f, 1f
        )
    }

    fun playTeamChanged(player: IPlayerHandle) {
        player.playSound(
            PlayerSoundEvent.ITEM_LODESTONE_COMPASS_LOCK,
            PlayerSoundCategory.BLOCKS,
            1f, 1f
        )
    }

}