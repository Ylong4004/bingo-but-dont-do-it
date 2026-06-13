package me.jfenn.bingo.common.autorestart

import me.jfenn.bingo.common.config.BingoConfig
import me.jfenn.bingo.common.event.ScopedEvents
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.state.GameState
import me.jfenn.bingo.platform.IExecutors
import me.jfenn.bingo.platform.IModEnvironment
import me.jfenn.bingo.platform.IPlayerManager
import org.slf4j.Logger
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

internal class ResetOnLeaveController(
    private val log: Logger,
    private val config: BingoConfig,
    private val state: BingoState,
    private val resetService: ResetService,
    private val playerManager: IPlayerManager,
    private val serverTaskExecutor: IExecutors.IServerTaskExecutor,
    environment: IModEnvironment,
    events: ScopedEvents,
) {
    private fun checkRestartOnLeave() {
        if (config.nextRoundWhenEveryoneDisconnects && state.isLobbyMode && state.state == GameState.POSTGAME && playerManager.getPlayers().isEmpty()) {
            log.info("[ResetOnLeaveController] Last player has disconnected and 'nextRoundWhenEveryoneDisconnects' is true; Resetting...")
            serverTaskExecutor.execute {
                resetService.resetGame()
            }
        }
    }

    init {
        // This should only ever run on the server env
        if (environment.envType == IModEnvironment.EnvType.SERVER) {
            events.onPlayerDisconnect {
                CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS, serverTaskExecutor)
                    .execute { checkRestartOnLeave() }
            }
        }
    }
}