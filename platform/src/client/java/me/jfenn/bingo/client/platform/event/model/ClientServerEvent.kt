package me.jfenn.bingo.client.platform.event.model

import me.jfenn.bingo.platform.event.IEvent

class ClientServerEvent {
    object Join : IEvent<ClientServerEvent>
    object Disconnect : IEvent<ClientServerEvent>
    object ChannelRegister : IEvent<ClientServerEvent>
}