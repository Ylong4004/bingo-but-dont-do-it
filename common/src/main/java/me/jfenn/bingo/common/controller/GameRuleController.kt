package me.jfenn.bingo.common.controller

import me.jfenn.bingo.common.event.ScopedEvents
import me.jfenn.bingo.common.options.BingoOptions
import me.jfenn.bingo.common.scope.BingoComponent
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.state.GameState
import me.jfenn.bingo.mixinhandler.GameRuleOverrideHelper
import me.jfenn.bingo.platform.IServerWorldFactory
import me.jfenn.bingo.platform.world.IGameRules

/**
 * Controls server setting and gamerule changes:
 * - Hides advancements/death messages in PREGAME
 * - Applies game configuration (pvp, keepinv) when PLAYING
 */
internal class GameRuleController(
    events: ScopedEvents,
    private val options: BingoOptions,
    private val state: BingoState,
    private val datapackFunctionService: DatapackFunctionService,
    private val gameRules: IGameRules,
    private val serverWorldFactory: IServerWorldFactory,
) : BingoComponent() {

    fun readFromServer() {
        options.isPvpEnabled = gameRules.pvp.value
        options.isKeepInventory = gameRules.keepInventory.value
    }

    fun writeToServer() {
        if (!state.isLobbyMode) return
        gameRules.pvp.value = options.isPvpEnabled
        gameRules.keepInventory.value = options.isKeepInventory
    }

    private fun setPregameRules() {
        // hide spammy messages in the lobby
        GameRuleOverrideHelper.setOverride(gameRules.announceAdvancements, false)
        GameRuleOverrideHelper.setOverride(gameRules.showDeathMessages, false)

        // set up server with the initial config game rules
        writeToServer()
    }

    private fun setCountdownRules() {
        // reset overworld time
        serverWorldFactory.overworld.timeOfDay = 0
    }

    private fun setPlayingRules(setTimeOfDay: Boolean) {
        // revert the lobby gamerule changes
        GameRuleOverrideHelper.setOverride(gameRules.announceAdvancements, null)
        GameRuleOverrideHelper.setOverride(gameRules.showDeathMessages, null)

        // update server to match config choices
        writeToServer()

        // reset overworld time
        if (setTimeOfDay) {
            serverWorldFactory.overworld.timeOfDay = 0
        }
    }

    init {
        events.onStateChange { (from, to) ->
            if (from != to && state.isLobbyMode) {
                when (to) {
                    GameState.PREGAME, GameState.POSTGAME -> setPregameRules()
                    GameState.COUNTDOWN -> setCountdownRules()
                    GameState.PLAYING -> setPlayingRules(
                        // Don't change the time of day if resuming from POSTGAME
                        setTimeOfDay = from != GameState.POSTGAME
                    )
                    else -> {}
                }
            }

            if (from != to && from != GameState.POSTGAME) {
                datapackFunctionService.runStateChange(to)
            }
        }

        events.onUpdateTick {
            if (state.state == GameState.PLAYING) {
                // update config with the current game rules (in case /gamerule is used mid-game)
                readFromServer()
            }
        }

        events.onClose {
            GameRuleOverrideHelper.gameRuleOverrides.clear()
        }
    }

}