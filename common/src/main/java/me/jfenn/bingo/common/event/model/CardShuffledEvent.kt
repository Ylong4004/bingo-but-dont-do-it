package me.jfenn.bingo.common.event.model

import me.jfenn.bingo.platform.event.IEvent
import java.util.*

class CardShuffledEvent(
    val cardId: UUID,
) {
    companion object : IEvent<CardShuffledEvent>
}