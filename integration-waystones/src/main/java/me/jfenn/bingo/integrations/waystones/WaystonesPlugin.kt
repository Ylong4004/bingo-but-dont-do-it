package me.jfenn.bingo.integrations.waystones

import me.jfenn.bingo.platform.event.IEventBus
import me.jfenn.bingo.platform.event.game.GameResetEvent
import net.blay09.mods.waystones.api.WaystonesAPI
import net.minecraft.server.MinecraftServer
import org.slf4j.Logger

internal class WaystonesPlugin(
    private val log: Logger,
    private val server: MinecraftServer,
    events: IEventBus,
) {
    init {
        events.register(GameResetEvent) {
            log.info("[WaystonesPlugin] Game resetting...")

            WaystonesAPI.getAllWaystones(server)
                .toList()
                .forEach {
                    log.debug("[WaystonesPlugin] Removing waystone {}", it.waystoneUid)
                    WaystonesAPI.removeWaystoneFromDatabase(server, it)
                }
        }
    }
}