package me.jfenn.bingo.platform.event.model

import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.event.IEvent

data class PlayerEvent(
    val player: IPlayerHandle
) {
    object AfterRespawn: IEvent<PlayerEvent>
    object ChannelRegister: IEvent<PlayerEvent>
    object Disconnect: IEvent<PlayerEvent>
    /**
     * Note: this event is NOT called on the server thread!
     */
    object Init: IEvent<PlayerEvent>
    object Join: IEvent<PlayerEvent>
}
