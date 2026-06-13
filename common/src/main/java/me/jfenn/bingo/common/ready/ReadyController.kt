package me.jfenn.bingo.common.ready

import me.jfenn.bingo.common.KEYBIND_OPEN_CARD
import me.jfenn.bingo.common.Permission
import me.jfenn.bingo.common.autorestart.ResetService
import me.jfenn.bingo.common.commands.formatWarning
import me.jfenn.bingo.common.config.BingoConfig
import me.jfenn.bingo.common.event.ScopedEvents
import me.jfenn.bingo.common.event.packet.ServerPacketEvents
import me.jfenn.bingo.common.game.GameService
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.state.GameState
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.common.utils.formatHHMMSS
import me.jfenn.bingo.generated.StringKey
import me.jfenn.bingo.integrations.permissions.IPermissionsApi
import me.jfenn.bingo.platform.IExecutors
import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.IPlayerManager
import me.jfenn.bingo.platform.text.IText
import net.minecraft.text.Text
import org.slf4j.Logger
import java.time.Duration

internal class ReadyController(
    private val log: Logger,
    private val readyState: ReadyTimerState,
    private val state: BingoState,
    private val config: BingoConfig,
    private val text: TextProvider,
    private val gameService: GameService,
    private val playerManager: IPlayerManager,
    private val resetService: ResetService,
    private val packets: ServerPacketEvents,
    private val permissions: IPermissionsApi,
    private val readyService: ReadyService,
    private val serverTaskExecutor: IExecutors.IServerTaskExecutor,
    events: ScopedEvents,
) {

    private fun update(
        playersToUpdate: List<IPlayerHandle> = playerManager.getPlayers(),
    ) {
        if (!state.isLobbyMode) return
        // Only send update packets during these gamestates
        // (note that SpawnPreloadingController also sends ReadyUpdatePacket, so this must avoid conflicts)
        if (state.state !in arrayOf(GameState.PREGAME, GameState.POSTGAME))
            return

        val isVotingEnabled = when (state.state) {
            GameState.PREGAME -> config.startWhenReadySeconds != null
            GameState.POSTGAME -> config.nextRoundWhenReadySeconds != null
            else -> false
        }

        readyState.updatePlayers(playerManager.getPlayers().filter { readyService.canUseReady(it) })
        val remainingTime = readyState.remainingTime()
        val isComplete = remainingTime?.let { it <= Duration.ZERO } ?: false

        readyService.tick()

        if (isComplete && state.state == GameState.PREGAME) {
            log.info("[ReadyController] Reached the end of the PREGAME timer - starting the game!")
            readyService.reset()
            val warnings = mutableListOf<IText>()
            gameService.start(warnings, allowSpectators = true)
            warnings.forEachIndexed { i, warning ->
                playerManager.getPlayers()
                    .forEach { it.sendMessage(text.formatWarning(warning, i == 0)) }
            }
        }

        if (isComplete && state.state == GameState.POSTGAME) {
            log.info("[ReadyController] Reached the end of the POSTGAME timer - running reset.")
            serverTaskExecutor.execute {
                resetService.resetGame()
            }
        }

        val updatePacket = ReadyUpdatePacket(
            isRunning = readyState.isRunning && !isComplete,
            isReady = false,
            state = state.state,
            remainingDuration = readyState.remainingTime() ?: Duration.ZERO,
            totalDuration = readyState.totalTime() ?: Duration.ZERO,
            readyPlayers = playerManager.getPlayers().count { readyState.isReady(it.uuid) },
            totalPlayers = playerManager.getPlayers().size,
            title = null,
            subtitle = null,
        )

        for (player in playersToUpdate) {
            val isPlayerReady = readyState.isReady(player.uuid)
            val title = when {
                isPlayerReady -> text.string(StringKey.LobbyStartingPlayersReady, updatePacket.readyPlayers, updatePacket.totalPlayers)
                readyService.canUseReady(player) -> text.string(
                    StringKey.LobbyStartingReadyUp,
                    Text.keybind("key.sneak"), Text.keybind(KEYBIND_OPEN_CARD)
                )
                else -> text.empty()
            }
            val subtitle = when {
                state.state == GameState.PREGAME -> text.string(StringKey.LobbyStartingTimeRemaining, updatePacket.remainingDuration.formatHHMMSS())
                else -> text.string(StringKey.LobbyNextRoundTimeRemaining, updatePacket.remainingDuration.formatHHMMSS())
            }

            val packet = updatePacket.copy(
                isReady = isPlayerReady,
                title = title,
                subtitle = subtitle,
                canSendReady = isVotingEnabled && permissions.hasPermission(player, Permission.COMMAND_READY)
            )

            when {
                packets.readyUpdateV3.send(player, packet) -> {}
                packets.readyUpdateV2.send(player, packet) -> {}
                packets.readyUpdateV1.send(player, packet) -> {}
                packet.isRunning -> {
                    val message = when {
                        !isPlayerReady -> text.string(
                            when (state.state) {
                                GameState.POSTGAME -> StringKey.LobbyLeavingInRunCommand
                                else -> StringKey.LobbyStartingInRunCommand
                            },
                            packet.remainingDuration.formatHHMMSS()
                        )
                        else -> text.string(
                            when (state.state) {
                                GameState.POSTGAME -> StringKey.LobbyLeavingInReady
                                else -> StringKey.LobbyStartingInReady
                            },
                            packet.remainingDuration.formatHHMMSS(),
                            packet.readyPlayers,
                            packet.totalPlayers
                        )
                    }
                    player.sendHotbarMessage(message)
                }
            }
        }
    }

    init {
        events.onStateChange {
            readyService.reset()

            // Run an update check immediately
            update()

            // If the timer is not running, send an extra packet to dismiss the ready GUI
            if (!readyState.isRunning) {
                val packet = ReadyUpdatePacket(
                    isRunning = false,
                    isReady = false,
                    state = state.state,
                    remainingDuration = Duration.ZERO,
                    totalDuration = Duration.ZERO,
                    readyPlayers = 0,
                    totalPlayers = 0,
                    title = null,
                    subtitle = null,
                )
                for (player in playerManager.getPlayers()) {
                    when {
                        packets.readyUpdateV3.send(player, packet) -> {}
                        packets.readyUpdateV2.send(player, packet) -> {}
                        packets.readyUpdateV1.send(player, packet) -> {}
                    }
                }
            }
        }

        events.onChangeOptions {
            if (readyState.isRunning) {
                log.info("[ReadyController] Reset the timer, as the game options have changed")
            }
            readyService.reset()
        }

        events.onChangeTeam {
            // If the player leaves their team, un-ready them
            if (it.team == null) {
                readyState.setReady(it.player.uuid, false)
                playerManager.updatePlayerListName(it.player)
            }
            // If the player changes team while ready, update the tab name
            if (readyState.isReady(it.player.uuid)) {
                playerManager.updatePlayerListName(it.player)
            }
        }

        events.onPacket(packets.readySetV1) {
            val isReady = it.packet.isReady
            val result = readyService.setReady(it.player, isReady)
            // Only send the chat message if the player is in multiplayer (or if it failed)
            if (!state.isSingleplayer() || result !is ReadyResult.ChangedTo) {
                it.player.sendMessage(result.message)
            }
        }

        events.onPlayerChannelRegister {
            update(listOf(it.player))
        }

        events.onUpdateTick {
            update()
        }
    }
}