package me.jfenn.bingo.platform.event.model

import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.commands.ISignedMessage
import me.jfenn.bingo.platform.event.IReturnEvent

data class AllowChatMessageEvent(
    val message: ISignedMessage,
    val player: IPlayerHandle,
) {
    companion object : IReturnEvent<AllowChatMessageEvent, Boolean>
}
