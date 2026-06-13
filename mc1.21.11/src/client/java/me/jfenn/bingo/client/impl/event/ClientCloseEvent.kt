package me.jfenn.bingo.client.impl.event

import me.jfenn.bingo.platform.event.IEventBus
import me.jfenn.bingo.platform.event.model.ApplicationCloseEvent
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents

internal class ClientCloseEvent(
    private val eventBus: IEventBus,
)  {
    init {
        ClientLifecycleEvents.CLIENT_STOPPING.register {
            eventBus.emit(ApplicationCloseEvent, Unit)
        }
    }
}