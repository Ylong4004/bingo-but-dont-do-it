package me.jfenn.bingo.common.game

import me.jfenn.bingo.common.Permission
import me.jfenn.bingo.common.map.CardViewService
import me.jfenn.bingo.common.options.*
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.state.GameState
import me.jfenn.bingo.integrations.permissions.IPermissionsApi
import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.IPlayerManager
import me.jfenn.bingo.platform.IServerWorldFactory
import me.jfenn.bingo.platform.PlayerGameMode
import me.jfenn.bingo.platform.event.IEventBus
import org.slf4j.Logger
import java.time.Duration

internal class GameResumeService(
    private val log: Logger,
    private val state: BingoState,
    private val options: BingoOptions,
    private val optionsService: OptionsService,
    private val cardViewService: CardViewService,
    private val permissions: IPermissionsApi,
    private val playerManager: IPlayerManager,
    private val serverWorldFactory: IServerWorldFactory,
    private val eventBus: IEventBus,
) {

    fun isResumeAvailable(player: IPlayerHandle?): Boolean {
        val hasPermission = player?.let {
            permissions.hasPermission(it, Permission.CONFIGURE_GAME)
        } ?: true

        return state.isLobbyMode &&
                hasPermission &&
                state.state == GameState.POSTGAME &&
                // If a team won because of an auto-win (stalemate in lockout mode), the game cannot be resumed
                state.teams.values.none { it.completedCards.lastOrNull()?.isAutoWin == true }
    }

    fun resume() {
        require(state.state == GameState.POSTGAME) { "Game can only be resumed from POSTGAME! (is ${state.state})" }
        val gameOverInfo = requireNotNull(state.gameOverInfo) { "gameOverInfo is null!" }
        log.info("[GameService] Resuming the game!")

        val context = OptionsService.Context(
            player = null,
            receiveFeedback = { message ->
                log.info("[GameService] Resume: $message")
                playerManager.getPlayers().forEach { it.sendMessage(message) }
            },
            receiveError = null,
        )

        val teams = state.getRegisteredTeams()

        // Find if any card can just be resumed with no changes
        val canResumeAllCards = teams.all { team ->
            team.winner == null && team.completedCards.lastOrNull()?.isWinner == false
        }

        // Find if any winning card has a goal that can be increased
        val canIncreaseCardGoal = teams.firstNotNullOfOrNull { team ->
            val lastCard = team.completedCards.lastOrNull()
                ?: return@firstNotNullOfOrNull null
            if (
                lastCard.isWinner &&
                (!lastCard.card.options.goal.isFullCard() ||
                        options.winCondition is BingoWinCondition.ReplaceGoals)
            ) {
                lastCard
            } else null
        }

        if (canResumeAllCards) {
            // Resume each team's last completed card
            for (team in teams) {
                val resumeCard = team.completedCards.removeLast()
                team.cardId = resumeCard.card.id
            }
        } else if (canIncreaseCardGoal != null) {
            val cardId = canIncreaseCardGoal.card.id

            // Remove the team's last completedCard and restore the cardIds
            for (team in teams) {
                team.cardId = cardId
                team.completedCards.removeIf { it.card.id == cardId }
                team.winner = null
            }

            // if the game ended because a team won, and the goal wasn't full card, increase it!
            val newGoal = if (options.winCondition is BingoWinCondition.ReplaceGoals) {
                when (
                    val goal = canIncreaseCardGoal.card.options.goal
                ) {
                    is BingoGoal.Lines -> BingoGoal.Lines(goal.lines + 5)
                    is BingoGoal.Items -> BingoGoal.Items(goal.items + 25)
                }
            } else {
                BingoGoal.Items(BingoGoal.MAX_ITEMS)
            }

            val actualCard = state.cards.find { it.id == cardId }
            if (actualCard != null) {
                state.restoreOptions.add(RestoreGoal(actualCard.options.goal))
                optionsService.setGoal(context, actualCard, newGoal)
            }
        } else if (teams.any { !it.isWinner() }) {
            // if there are teams that aren't done yet, set endWhen=AllWin
            state.restoreOptions.add(RestoreEndWhen(options.endGameWhen))
            optionsService.setEndWhen(context, EndWhen.AllWin)
        } else {
            // Reset each team to a non-winner state
            for (team in teams) {
                team.winner = null
            }

            // if the team won for another reason (e.g. a multi-card game), set the winCondition to infinite
            state.restoreOptions.add(RestoreWinCondition(options.winCondition))
            optionsService.setWinCondition(context, BingoWinCondition.Infinite)
        }

        for (team in teams) {
            // If in lobby mode, restore each player's inventory/position (if they continue the game)
            if (state.isLobbyMode && !team.isWinner()) {
                val players = team.players.iterator()
                for (player in players) {
                    val playerImpl = playerManager.getPlayer(player.uuid)
                    // If a player isn't online when resumed, they'll get reset by PlayerController
                    // (and lose their inventory... bad luck. sorry)
                        ?: continue

                    val playerData = gameOverInfo.playerStates[player.uuid] ?: continue

                    playerImpl.gameMode = PlayerGameMode.SURVIVAL
                    playerImpl.teleport(
                        world = serverWorldFactory.listWorlds()
                            .find { it.identifier == playerData.world }
                            ?: continue,
                        pos = playerData.position,
                        yaw = playerData.yaw,
                        pitch = playerData.pitch,
                    )

                    playerData.inventory.forEach { (slot, stack) ->
                        playerImpl.setStack(slot, stack)
                    }
                }
            }
        }

        // If the time limit is close (or expired), remove it!
        if (state.remainingDuration()?.let { it < Duration.ofSeconds(30) } == true) {
            optionsService.createRestoreTimeLimit()
            optionsService.setTimeLimit(context, null)
        }

        // If the game ended because of a stalemate, set stalemates to "Do Nothing"
        when (gameOverInfo.reason) {
            is GameEndReason.Lockout,
            is GameEndReason.Stalemate,
            is GameEndReason.ImpossibleGoal -> {
                state.restoreOptions.add(RestoreStalemateBehavior(options.stalemateBehavior))
                optionsService.setStalemateBehavior(context, StalemateBehavior.NOTHING)
            }
            else -> {}
        }

        // Clear game over info
        state.gameOverInfo = null
        state.isForfeit = false

        // Restore timer info (TimerCheck resets startedAt on playing)
        state.startedAt = null
        state.updatedAt = null
        state.endedAt = null
        state.timeAdjustment = gameOverInfo.duration
        state.timeOffline = Duration.ZERO

        // Send an empty card display packet to reset the HUD state
        // (this is needed to clear the client gameOver state)
        for (player in playerManager.getPlayers()) {
            cardViewService.sendClearDisplayPacket(player)
        }

        // Resume the game
        state.changeState(eventBus, GameState.PLAYING)
    }

}