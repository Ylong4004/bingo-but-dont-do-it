package me.jfenn.bingo.client.common.settings

import me.jfenn.bingo.client.common.packet.ClientPacketEvents
import me.jfenn.bingo.client.platform.event.model.ClientServerEvent
import me.jfenn.bingo.platform.event.IEventBus
import org.slf4j.Logger

internal class ClientSettingsController(
    private val log: Logger,
    private val clientSettings: ClientSettingsService,
    private val packetEvents: ClientPacketEvents,
    eventBus: IEventBus,
) {

    init {
        eventBus.register(packetEvents.receivePlayerSettingsV1) { (packet) ->
            log.debug("[ClientSettingsController] Received a settings update (v1) from the server...")
            clientSettings.update(packet, sendPacket = false)
        }

        eventBus.register(packetEvents.receivePlayerSettingsV2) { (packet) ->
            log.debug("[ClientSettingsController] Received a settings update (v2) from the server...")
            clientSettings.update(packet, sendPacket = false)
        }

        eventBus.register(ClientServerEvent.ChannelRegister) {
            log.debug("[ClientSettingsController] Sending initial settings to the server...")
            val settings = clientSettings.getSettings()
            when {
                packetEvents.sendPlayerSettingsV2.send(settings) -> {}
                packetEvents.sendPlayerSettingsV1.send(settings) -> {}
            }
        }
    }

}