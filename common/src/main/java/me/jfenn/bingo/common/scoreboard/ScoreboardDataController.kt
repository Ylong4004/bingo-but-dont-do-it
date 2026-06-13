package me.jfenn.bingo.common.scoreboard

import me.jfenn.bingo.common.event.model.ScoreChangedEvent
import me.jfenn.bingo.common.event.model.StateChangedEvent
import me.jfenn.bingo.common.event.model.TeamChangedEvent
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.team.TeamService
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.generated.StringKey
import me.jfenn.bingo.platform.IExecutors
import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.IPlayerManager
import me.jfenn.bingo.platform.event.IEventBus
import me.jfenn.bingo.platform.event.model.PlayerEvent
import me.jfenn.bingo.platform.scoreboard.IObjectiveHandle
import me.jfenn.bingo.platform.scoreboard.IScoreboardManager

internal class ScoreboardDataController(
    private val state: BingoState,
    private val playerManager: IPlayerManager,
    private val teamService: TeamService,
    private val scoreboardManager: IScoreboardManager,
    private val serverTaskExecutor: IExecutors.IServerTaskExecutor,
    private val text: TextProvider,
    eventBus: IEventBus,
) {

    private fun createItemsObjective(): IObjectiveHandle {
        return scoreboardManager.createDummyObjective("bingo_items")
            .apply {
                displayName = text.string(StringKey.GoalItems)
            }
    }

    private fun createLinesObjective(): IObjectiveHandle {
        return scoreboardManager.createDummyObjective("bingo_lines")
            .apply {
                displayName = text.string(StringKey.GoalLines)
            }
    }

    private fun createCardsObjective(): IObjectiveHandle {
        return scoreboardManager.createDummyObjective("bingo_cards")
            .apply {
                displayName = text.string(StringKey.GoalCards)
            }
    }

    private fun updatePlayers(players: List<IPlayerHandle>) {
        val items = createItemsObjective()
        val lines = createLinesObjective()
        val cards = createCardsObjective()

        for (player in players) {
            val team = teamService.getPlayerTeam(player)
            items.setPlayer(player, team?.score?.items ?: 0)
            lines.setPlayer(player, team?.score?.lines ?: 0)
            cards.setPlayer(player, team?.score?.cards ?: 0)
        }
    }

    init {
        if (state.isLobbyMode) {
            eventBus.register(StateChangedEvent) {
                createItemsObjective()
                createLinesObjective()
                createCardsObjective()
            }

            eventBus.register(ScoreChangedEvent) { event ->
                state.teams[event.team]?.players
                    ?.mapNotNull { playerManager.getPlayer(it.uuid) }
                    ?.let { updatePlayers(it) }
            }

            eventBus.register(PlayerEvent.Join) {
                serverTaskExecutor.execute {
                    updatePlayers(listOf(it.player))
                }
            }

            eventBus.register(TeamChangedEvent) {
                updatePlayers(listOf(it.player))
            }
        }
    }
}