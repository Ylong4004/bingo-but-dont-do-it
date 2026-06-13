package me.jfenn.bingo.platform.event.model

import me.jfenn.bingo.platform.event.IEvent

class TickEvent(
    val ticks: Int,
) {
    object Start : IEvent<TickEvent>
    object End : IEvent<TickEvent>
}