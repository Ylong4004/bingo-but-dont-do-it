package me.jfenn.bingo.common.timer

import me.jfenn.bingo.common.config.BingoConfig
import me.jfenn.bingo.common.event.ScopedEvents
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.state.GameState
import me.jfenn.bingo.common.team.TeamService
import me.jfenn.bingo.common.utils.milliseconds
import me.jfenn.bingo.common.utils.minus
import me.jfenn.bingo.common.utils.seconds
import me.jfenn.bingo.mixinhandler.ServerPlayNetworkHandlerMixinHandler
import me.jfenn.bingo.platform.IPlayerManager
import me.jfenn.bingo.platform.event.IEventBus
import org.slf4j.Logger
import java.time.Instant

/**
 * Wait until the player has finished loading terrain
 */
internal class WaitUntilLoadedController(
    events: ScopedEvents,
    eventBus: IEventBus,
    private val config: BingoConfig,
    private val state: BingoState,
    private val countdownService: CountdownService,
    private val teamService: TeamService,
    private val playerManager: IPlayerManager,
    private val log: Logger,
) {

    private var startedLoading: Instant? = null

    init {
        events.onEnter(GameState.LOADING) {
            startedLoading = Instant.now()
            log.info("Waiting until all players have loaded spawn terrain...")
        }

        events.onGameTick {
            if (state.state != GameState.LOADING) return@onGameTick

            // +2 ticks to ensure that previous movement events are excluded
            val startedLoading = (startedLoading ?: return@onGameTick) + 40.milliseconds

            val isEveryPlayerLoaded = playerManager.getPlayers()
                .filter { teamService.isPlaying(it) }
                .all { player ->
                    val lastMovementPacket = ServerPlayNetworkHandlerMixinHandler.getLastPlayerMovement(player.player)
                    lastMovementPacket != null && lastMovementPacket > startedLoading
                }

            if (!isEveryPlayerLoaded || Instant.now() - startedLoading > 40.seconds) return@onGameTick
            log.info("Waiting until all players have loaded spawn terrain... Done! (${(Instant.now() - startedLoading)})")

            // change to the playing state
            if (config.countdownDelayTicks > 0 || config.countdownSeconds > 0) {
                state.changeState(eventBus, GameState.COUNTDOWN)
            } else {
                state.changeState(eventBus, GameState.PLAYING)
                countdownService.sendCountdownPacket(CountdownPacket(0))
            }
        }
    }
}