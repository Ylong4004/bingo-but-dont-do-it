package me.jfenn.bingo.common.event.model

import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.event.IEvent

data class PlayerSettingsEvent(
    val player: IPlayerHandle,
) {
    companion object : IEvent<PlayerSettingsEvent>
}