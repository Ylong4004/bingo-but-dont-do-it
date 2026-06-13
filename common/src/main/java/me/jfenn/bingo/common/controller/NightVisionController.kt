package me.jfenn.bingo.common.controller

import me.jfenn.bingo.platform.EffectType
import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.IPlayerManager
import me.jfenn.bingo.common.config.BingoConfig
import me.jfenn.bingo.common.options.BingoOptions
import me.jfenn.bingo.common.config.PlayerSettingsService
import me.jfenn.bingo.common.event.ScopedEvents
import me.jfenn.bingo.common.scope.BingoComponent
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.state.GameState
import me.jfenn.bingo.common.team.TeamService
import org.slf4j.Logger

internal class NightVisionController(
    private val log: Logger,
    private val config: BingoConfig,
    private val state: BingoState,
    private val options: BingoOptions,
    private val teamService: TeamService,
    private val playerSettingsService: PlayerSettingsService,
    private val playerManager: IPlayerManager,
    events: ScopedEvents,
) : BingoComponent() {

    private fun updateNightVision(player: IPlayerHandle) {
        val isOnTeam = teamService.isPlaying(player)

        // give the player night_vision if configured
        // ...and remove it if not
        if (
            config.nightVisionInPostgame || config.nightVisionInSpectator || options.isNightVision
        ) {
            val shouldHaveNightVision = (
                    (state.state.isPlayingOrCountdown && !isOnTeam && config.nightVisionInSpectator && state.isLobbyMode) ||
                            (state.state == GameState.POSTGAME && config.nightVisionInPostgame && state.isLobbyMode) ||
                            (state.state.isPlayingOrCountdown && isOnTeam && options.isNightVision)
                    ) && playerSettingsService.getPlayer(player).nightVision

            if (shouldHaveNightVision) {
                val hasEffect = player.getEffects().any { it.type == EffectType.NIGHT_VISION }
                if (!hasEffect) {
                    log.debug("[NightVisionController] Adding night vision to {}", player.playerName)
                    player.addEffect(
                        type = EffectType.NIGHT_VISION,
                        duration = -1,
                        amplifier = 0,
                        ambient = false,
                        visible = false
                    )
                }
            } else {
                // if the player has infinite night vision (and should not), remove it
                val effect = player.getEffects().find { it.type == EffectType.NIGHT_VISION }
                if (effect?.duration == -1) {
                    log.debug("[NightVisionController] Removing night vision from {}", player.playerName)
                    player.removeEffect(effect)
                }
            }
        }
    }

    init {
        events.onUpdateTick {
            for (player in playerManager.getPlayers()) {
                // Night vision is apparently super buggy and doesn't apply if invoked in `resetPlayerHealth()`...
                // (seems like effects get cleared in a few other places - so we need to make sure it persists)
                updateNightVision(player)
            }
        }
    }

}