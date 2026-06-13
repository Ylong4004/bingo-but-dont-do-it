package me.jfenn.bingo.impl.event

import me.jfenn.bingo.platform.event.model.ApplicationCloseEvent
import me.jfenn.bingo.platform.event.IEventBus
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents

class ServerCloseEvent(
    private val eventBus: IEventBus,
) {
    init {
        ServerLifecycleEvents.SERVER_STOPPING.register {
            eventBus.emit(ApplicationCloseEvent, Unit)
        }
    }
}
