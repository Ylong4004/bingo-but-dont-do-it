package me.jfenn.bingo.client.common.settings

import me.jfenn.bingo.client.common.packet.ClientPacketEvents
import me.jfenn.bingo.client.platform.ISessionAccessor
import me.jfenn.bingo.common.config.PlayerSettings
import me.jfenn.bingo.common.config.PlayerSettingsService
import org.slf4j.Logger
import java.util.*

internal class ClientSettingsService(
    private val log: Logger,
    private val playerSettingsService: PlayerSettingsService,
    sessionAccessor: ISessionAccessor,
    private val packetEvents: ClientPacketEvents,
) {

    private val playerUuid = sessionAccessor.getPlayerUuid()
        ?: UUID.fromString("00000000-0000-0000-0000-000000000000")

    fun getSettings() = playerSettingsService.getPlayer(playerUuid)

    fun update(settings: PlayerSettings, sendPacket: Boolean = true) {
        playerSettingsService.writeAll(playerUuid, settings)

        if (sendPacket) {
            log.debug("[ClientSettingsService] Sending settings update for player")
            when {
                packetEvents.sendPlayerSettingsV2.send(settings) -> {}
                packetEvents.sendPlayerSettingsV1.send(settings) -> {}
            }
        }
    }

}