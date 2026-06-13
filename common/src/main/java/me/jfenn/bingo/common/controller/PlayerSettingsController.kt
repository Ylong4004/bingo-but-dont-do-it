package me.jfenn.bingo.common.controller

import me.jfenn.bingo.common.Permission
import me.jfenn.bingo.common.config.PlayerSettings
import me.jfenn.bingo.common.config.PlayerSettingsService
import me.jfenn.bingo.common.event.ScopedEvents
import me.jfenn.bingo.common.event.model.PlayerSettingsEvent
import me.jfenn.bingo.common.event.packet.ServerPacketEvents
import me.jfenn.bingo.common.scope.BingoComponent
import me.jfenn.bingo.integrations.permissions.IPermissionsApi
import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.event.IEventBus
import org.slf4j.Logger

internal class PlayerSettingsController(
    private val events: ScopedEvents,
    private val eventBus: IEventBus,
    private val log: Logger,
    private val permissions: IPermissionsApi,
    private val playerSettingsService: PlayerSettingsService,
    private val packetManager: ServerPacketEvents,
) : BingoComponent() {

    private fun receivePlayerSettings(player: IPlayerHandle, settings: PlayerSettings) {
        if (permissions.hasPermission(player, Permission.CONFIGURE_PLAYER)) {
            log.debug("[PlayerSettingsController] Server - processing settings change from {}", player)

            // Don't let clients sync hideLobbyPrompt to a server, as it should be install-specific
            val existingSettings = playerSettingsService.getPlayer(player)
            val newSettings = settings.copy(
                hideLobbyPrompt = existingSettings.hideLobbyPrompt
            )

            playerSettingsService.writeAll(player.uuid, newSettings)
            eventBus.emit(PlayerSettingsEvent, PlayerSettingsEvent(player))
        } else {
            log.debug(
                "[PlayerSettingsController] Server - denying settings change from {} due to permissions",
                player
            )
        }
    }

    init {
        eventBus.register(PlayerSettingsEvent) { (player) ->
            val settings = playerSettingsService.getPlayer(player)
            when {
                packetManager.receivePlayerSettingsV2.send(player, settings) -> {}
                packetManager.receivePlayerSettingsV1.send(player, settings) -> {}
            }
        }

        events.onPacket(packetManager.sendPlayerSettingsV1) {
            receivePlayerSettings(it.player, it.packet)
        }

        events.onPacket(packetManager.sendPlayerSettingsV2) {
            receivePlayerSettings(it.player, it.packet)
        }
    }

}