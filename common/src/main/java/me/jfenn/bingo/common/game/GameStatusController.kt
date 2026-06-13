package me.jfenn.bingo.common.game

import me.jfenn.bingo.common.event.ScopedEvents
import me.jfenn.bingo.common.event.model.OptionsChangedEvent
import me.jfenn.bingo.common.event.model.StateChangedEvent
import me.jfenn.bingo.common.event.packet.ServerPacketEvents
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.state.GameState
import me.jfenn.bingo.platform.IExecutors
import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.IPlayerManager
import me.jfenn.bingo.platform.event.IEventBus
import me.jfenn.bingo.platform.event.model.PlayerEvent
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

internal class GameStatusController(
    private val state: BingoState,
    private val packets: ServerPacketEvents,
    private val playerManager: IPlayerManager,
    private val serverTaskExecutor: IExecutors.IServerTaskExecutor,
    eventBus: IEventBus,
    scopedEvents: ScopedEvents
) {
    private fun sendGameStatus(
        players: List<IPlayerHandle>
    ) {
        val packet = GameStatusPacket(
            ingameDuration = state.ingameDuration(),
            remainingDuration = state.remainingDuration() ?: state.options.timeLimit,
        )

        for (player in players) {
            packets.gameStatusV1.send(player, packet)
        }
    }

    init {
        eventBus.register(PlayerEvent.ChannelRegister) {
            sendGameStatus(listOf(it.player))
        }

        eventBus.register(StateChangedEvent) {
            sendGameStatus(playerManager.getPlayers())

            // HUD state might reset if this is PREGAME
            CompletableFuture.delayedExecutor(1L, TimeUnit.SECONDS, serverTaskExecutor)
                .execute { sendGameStatus(playerManager.getPlayers()) }
        }

        eventBus.register(OptionsChangedEvent) {
            sendGameStatus(playerManager.getPlayers())
        }

        scopedEvents.onUpdateTick {
            if (state.state == GameState.PLAYING) {
                sendGameStatus(playerManager.getPlayers())
            }
        }
    }
}