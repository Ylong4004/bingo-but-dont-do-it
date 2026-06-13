package me.jfenn.bingo.common.scoring

import me.jfenn.bingo.common.event.model.StateChangedEvent
import me.jfenn.bingo.common.event.model.TeamChangedEvent
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.state.GameState
import me.jfenn.bingo.platform.IExecutors
import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.IPlayerManager
import me.jfenn.bingo.platform.event.IEventBus
import me.jfenn.bingo.platform.event.model.PlayerEvent

internal class GameMessageController(
    private val state: BingoState,
    private val gameMessageService: GameMessageService,
    private val serverTaskExecutor: IExecutors.IServerTaskExecutor,
    private val playerManager: IPlayerManager,
    eventBus: IEventBus
) {
    private fun scheduleResendMessages(player: IPlayerHandle) {
        state.gameMessages
            .filter { gameMessageService.isEnabled(player, it) }
            .chunked(16)
            .forEach { messages ->
                serverTaskExecutor.execute {
                    for (message in messages) {
                        gameMessageService.createMessagePacket(player, message)
                            ?.copy(isUpdate = true)
                            ?.let { gameMessageService.sendMessagePacket(player, it) }
                    }
                }
            }
    }

    init {
        eventBus.register(PlayerEvent.ChannelRegister) {
            scheduleResendMessages(it.player)
        }

        eventBus.register(TeamChangedEvent) {
            gameMessageService.sendClearMessagesPacket(it.player)
            scheduleResendMessages(it.player)
        }

        eventBus.register(StateChangedEvent) {
            if (it.to == GameState.PREGAME) {
                for (player in playerManager.getPlayers()) {
                    gameMessageService.sendClearMessagesPacket(player)
                }
            }
            if (it.to == GameState.POSTGAME) {
                for (player in playerManager.getPlayers()) {
                    scheduleResendMessages(player)
                }
            }
        }
    }
}