package me.jfenn.bingo.platform.event.game

import me.jfenn.bingo.platform.event.IEvent
import org.koin.core.scope.Scope

class ScopeStopped(
    val scope: Scope
) {
    companion object : IEvent<ScopeStopped>
}