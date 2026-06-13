package me.jfenn.bingo.client.impl.event

import me.jfenn.bingo.client.impl.screen.ScreenHelperImpl
import me.jfenn.bingo.client.platform.event.model.*
import me.jfenn.bingo.platform.event.IEventBus
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.C2SPlayChannelEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.rendering.v1.InvalidateRenderStateCallback
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.resource.ResourceManagerHelper
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener
import net.minecraft.client.gui.screen.TitleScreen
import net.minecraft.resource.ResourceManager
import net.minecraft.resource.ResourceType
import net.minecraft.util.Identifier

class ClientEventsImpl(
    private val eventBus: IEventBus,
) {
    init {
        ClientPlayConnectionEvents.JOIN.register { _, _, _ ->
            eventBus.emit(ClientServerEvent.Join, ClientServerEvent())
        }

        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            eventBus.emit(ClientServerEvent.Disconnect, ClientServerEvent())
        }

        C2SPlayChannelEvents.REGISTER.register { _, _, _, _ ->
            eventBus.emit(ClientServerEvent.ChannelRegister, ClientServerEvent())
        }

        InvalidateRenderStateCallback.EVENT.register {
            eventBus.emit(InvalidateRenderStateEvent, InvalidateRenderStateEvent())
        }

        ClientTickEvents.END_CLIENT_TICK.register {
            eventBus.emit(ClientTickEvent.End, ClientTickEvent())
        }

        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(
            object : SimpleSynchronousResourceReloadListener {
                val identifier = Identifier.of("yet-another-minecraft-bingo", "bingo_client")!!

                override fun reload(manager: ResourceManager) {
                    eventBus.emit(ClientReloadEvent, ClientReloadEvent(manager))
                }

                override fun getFabricId(): Identifier = identifier
            }
        )

        ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            eventBus.emit(
                ScreenEvent.AfterInit,
                ScreenEvent(
                    type = when (screen) {
                        is TitleScreen -> ScreenType.TitleScreen
                        else -> ScreenType.Other
                    },
                    screen = ScreenHelperImpl(screen),
                )
            )
        }
    }
}
