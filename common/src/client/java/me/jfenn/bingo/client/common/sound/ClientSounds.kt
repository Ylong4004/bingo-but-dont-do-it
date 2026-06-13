package me.jfenn.bingo.client.common.sound

import me.jfenn.bingo.client.platform.IClientSoundManager
import me.jfenn.bingo.common.MOD_ID_BINGO

internal class ClientSounds(
    private val soundManager: IClientSoundManager,
) {

    // this enum must contain the exact file names in resources/assets/yet-another-minecraft-bingo/sounds/*.ogg
    // as well as a translation key under "bingo.sound.*"
    enum class Key {
        ITEM_SCORED,
        ITEM_LOST,
        OPPONENT_SCORED,
        GAME_WON,
        GAME_LOST,
        TIMER_TICK,
        START_COUNTDOWN,
        START_RELEASE;

        val id get() = "$MOD_ID_BINGO:${name.lowercase()}"
    }

    private val soundEvents = Key.entries.associateWith {
        soundManager.createUnregistered(it.id)
    }

    operator fun get(key: Key) = soundEvents[key]!!

}