package me.jfenn.bingo.client.platform.event.model

import me.jfenn.bingo.platform.event.IEvent

class ClientTickEvent {
    object End : IEvent<ClientTickEvent>
}