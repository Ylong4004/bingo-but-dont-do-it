package me.jfenn.bingo.integrations.ddi

import me.jfenn.bingo.common.event.ScopedEvents
import me.jfenn.bingo.common.game.DDIGameEndService
import me.jfenn.bingo.common.options.BingoOptions
import me.jfenn.bingo.common.options.DDIObjectiveMode
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.state.GameState
import net.minecraft.server.MinecraftServer
import org.slf4j.Logger
import java.util.UUID

sealed interface DDISessionState {
    data object Inactive : DDISessionState
    data class Active(val gameId: UUID) : DDISessionState
    data class Completed(val gameId: UUID) : DDISessionState
}

/** Binds one DDI session to the authoritative Bingo game scope. */
class DDIGameController(
    private val state: BingoState,
    private val options: BingoOptions,
    private val server: MinecraftServer,
    private val ddiManager: DDIObjectiveManager,
    private val gameEndService: DDIGameEndService,
    events: ScopedEvents,
    private val log: Logger,
) {
    var sessionState: DDISessionState = DDISessionState.Inactive
        private set

    private var isShutdown = false
    private var pendingResult: DDIRoundResult? = null

    init {
        ddiManager.onCompletedHandler = { result ->
            val active = sessionState as? DDISessionState.Active
            if (active != null) {
                sessionState = DDISessionState.Completed(active.gameId)
                pendingResult = result
                log.info("[DDI] Game {} completed", active.gameId)
            }
        }

        events.onStateChange { event -> reconcile(event.to) }
        events.onGameTick { event ->
            if (isShutdown) return@onGameTick
            // Capture only results that existed before this callback. A result
            // produced by the timer below waits for the next game tick, giving
            // ScoredItemCheck a complete tick to win any same-tick race first.
            val resultToFinalize = pendingResult
            ddiManager.enforceEliminatedSpectators()
            if (event.ticks % TICKS_PER_SECOND == 0 && sessionState is DDISessionState.Active) {
                ddiManager.tickWordTimers()
            }
            if (resultToFinalize != null && pendingResult === resultToFinalize) {
                pendingResult = null
                val winnerKey = (resultToFinalize as? DDIRoundResult.Winner)?.teamKey
                if (!gameEndService.end(winnerKey)) {
                    log.warn("[DDI] Could not apply deferred round result {}", resultToFinalize)
                }
            }
        }
        events.onPlayerChannelRegister { event ->
            // ScopedEvents also attempts this on Join; the packet handler's
            // support check makes ChannelRegister the reliable successful send.
            if (!isShutdown) ddiManager.resyncTo(event.player.player)
        }
        events.onPlayerDisconnect { event ->
            if (!isShutdown) ddiManager.onPlayerDisconnect(event.player.uuid)
        }
        events.onPlayerRespawn { event ->
            if (!isShutdown) ddiManager.enforceEliminatedSpectators()
        }
        events.onChangeTeam { event ->
            if (!isShutdown) ddiManager.onPlayerTeamChanged(event.player.uuid, event.team?.key)
        }

        // The plugin hook normally creates this controller before ScopeManager
        // replays the saved state. Reconcile here as well so construction is
        // safe from any other entry point.
        reconcile(state.state)
        log.info("[DDI] GameController initialized in state {}", sessionState)
    }

    private fun reconcile(gameState: GameState) {
        if (isShutdown) return
        when (gameState) {
            GameState.PLAYING -> ensureStarted()
            GameState.POSTGAME -> completeAndStop()
            GameState.PREGAME -> reset()
            else -> Unit
        }
    }

    private fun ensureStarted() {
        val gameId = state.gameId
        when (val current = sessionState) {
            is DDISessionState.Active -> if (current.gameId == gameId) return
            // /bingo resume returns the same gameId to PLAYING after POSTGAME
            // has cleared the previous DDI state. Start a fresh DDI segment in
            // that case instead of silently resuming Bingo without DDI.
            is DDISessionState.Completed -> Unit
            DDISessionState.Inactive -> Unit
        }

        // A new game can be observed without PREGAME when a save is resumed.
        // Clear any previous local session before using the new gameId.
        if (ddiManager.hasRound) ddiManager.stop(sendReset = true)

        if (!options.enableDDI) {
            sessionState = DDISessionState.Inactive
            log.debug("[DDI] Disabled for Bingo game {}", gameId)
            return
        }

        val registeredTeams = state.getRegisteredTeams()
            .filter { it.isPlaying() }
        val participatingTeams = when (options.ddiObjectiveMode) {
            DDIObjectiveMode.INDIVIDUAL -> registeredTeams
            DDIObjectiveMode.TEAM_SHARED -> registeredTeams.filter { team ->
                // Include all registered profiles from a participating team so
                // a temporarily offline member cannot later join as an
                // untracked spectator and receive their own team's word.
                team.players.any { server.playerManager.getPlayer(it.uuid) != null }
            }
        }
        val participants = participatingTeams
            .asSequence()
            .flatMap { team ->
                team.players.asSequence().map { profile ->
                    DDIParticipant(
                        profile = profile,
                        teamKey = team.key,
                        teamName = team.getSimpleName().toString(),
                        teamColor = team.textColor,
                    )
                }
            }
            // Individual objectives exist only for players who are online at
            // round start. Team mode filtered whole teams above and retains all
            // of each participating team's fixed registered roster.
            .filter {
                options.ddiObjectiveMode == DDIObjectiveMode.TEAM_SHARED ||
                    server.playerManager.getPlayer(it.profile.uuid) != null
            }
            .distinctBy { it.profile.uuid }
            .toList()
        val config = DDIRoundConfig.validated(
            maxHearts = options.ddiMaxHearts,
            wordTimerSeconds = options.ddiWordTimerSeconds,
            objectiveMode = options.ddiObjectiveMode,
        )

        // Set Active before start(), because resolving an initial instant word
        // can synchronously complete the DDI round through the callback above.
        sessionState = DDISessionState.Active(gameId)
        pendingResult = null
        val started = ddiManager.start(server, gameId, participants, config)
        if (!started) sessionState = DDISessionState.Completed(gameId)
    }

    private fun completeAndStop() {
        val gameId = state.gameId
        pendingResult = null
        ddiManager.stop(sendReset = true)
        sessionState = DDISessionState.Completed(gameId)
    }

    private fun reset() {
        pendingResult = null
        ddiManager.stop(sendReset = true)
        sessionState = DDISessionState.Inactive
    }

    /** Called by the internal plugin hook before the Koin scope is closed. */
    fun shutdown() {
        if (isShutdown) return
        isShutdown = true
        pendingResult = null
        ddiManager.stop(sendReset = false)
        sessionState = DDISessionState.Inactive
        log.info("[DDI] GameController shut down")
    }

    private companion object {
        const val TICKS_PER_SECOND = 20
    }
}
