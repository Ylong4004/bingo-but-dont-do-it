package me.jfenn.bingo.client.platform.event.model

import me.jfenn.bingo.platform.event.IEvent
import net.minecraft.resource.ResourceManager

class ClientReloadEvent(
    val resourceManager: ResourceManager,
) {
    companion object : IEvent<ClientReloadEvent>
}