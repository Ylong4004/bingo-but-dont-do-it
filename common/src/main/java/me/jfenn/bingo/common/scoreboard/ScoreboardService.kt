package me.jfenn.bingo.common.scoreboard

import me.jfenn.bingo.platform.text.IText
import me.jfenn.bingo.common.BINGO_TEAM_PREFIX
import me.jfenn.bingo.common.config.PlayerSettingsService
import me.jfenn.bingo.common.map.CardViewService
import me.jfenn.bingo.common.map.MapItemService
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.team.BingoTeam
import me.jfenn.bingo.common.team.TeamService
import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.IPlayerManager
import me.jfenn.bingo.platform.ITextSerializer
import me.jfenn.bingo.platform.PlayerGameMode
import me.jfenn.bingo.platform.scoreboard.IObjectiveHandle
import me.jfenn.bingo.platform.scoreboard.IScoreboardManager
import me.jfenn.bingo.platform.scoreboard.ScoreChange

internal class ScoreboardService(
    private val state: BingoState,
    private val scoreboardManager: IScoreboardManager,
    private val playerManager: IPlayerManager,
    private val playerSettingsService: PlayerSettingsService,
    private val cardViewService: CardViewService,
    private val mapItemService: MapItemService,
    private val textSerializer: ITextSerializer,
    private val teamService: TeamService,
) {

    fun getScoreboardObjective(team: BingoTeam?) : IObjectiveHandle {
        val objectiveName = "bingo_sidebar_" + team?.id?.removePrefix(BINGO_TEAM_PREFIX)
        val objective = scoreboardManager.createDummyObjective(objectiveName)
        return objective
    }

    fun shouldDisplayScoreboard(player: IPlayerHandle) : Boolean {
        // if the player is not on a team and isLobbyMode=false, then they should not see the scoreboard
        val isOnTeam = teamService.isPlaying(player)
        if (!state.isLobbyMode && !isOnTeam) return false

        // if the player has turned the scoreboard off
        val settings = playerSettingsService.getPlayer(player)
        if (!settings.scoreboard) return false
        if (!settings.scoreboardAutoHide) return true

        // auto-hide should not be used if the player is using the card HUD
        // (as the player will never be holding a card)
        if (cardViewService.supportsCardHud(player)) return true

        // if the player is holding the map item, show the scoreboard; otherwise, hide it
        val isHoldingMapItem = player.gameMode != PlayerGameMode.SURVIVAL
                || mapItemService.isMapItem(player.mainHandStack)
                || mapItemService.isMapItem(player.offHandStack)

        return isHoldingMapItem
    }

    private val scoreboardViews = mutableMapOf<String, ScoreboardView>()

    fun setScoreboardTitle(
        objective: IObjectiveHandle,
        title: IText,
        players: List<IPlayerHandle>,
    ) {
        if (title == objective.displayName) {
            return
        }

        objective.displayName = title
        for (player in players) {
            scoreboardManager.sendObjectiveDisplayUpdate(player, objective)
        }
    }

    fun setScoreboardContents(
        objective: IObjectiveHandle,
        contents: List<IText>,
        players: List<IPlayerHandle>,
    ) {
        val contentItems = contents.map {
            ScoreboardView.ScoreboardItem(textSerializer.toRawString(it.value), it.value)
        }

        // update the server-side objective
        //   note: the "value" is currently unused in this function
        scoreboardManager.setScoreboardText(objective, contentItems.map { ScoreChange.Create(it.string, it.text, 0) })

        val view = scoreboardViews.getOrPut(objective.name) { ScoreboardView(objective.name) }
        val changes = view.updateContents(contentItems)

        for (player in players) {
            if (!view.players.contains(player.uuid)) {
                // ensure that the player is only in one scoreboard view at a time
                for (otherView in scoreboardViews.values - view) {
                    if (otherView.players.contains(player.uuid)) {
                        val otherObjective = scoreboardManager.createDummyObjective(otherView.objectiveName)
                        scoreboardManager.sendObjectiveDelete(player, otherObjective)
                        otherView.players -= player.uuid
                    }
                }

                // if the player is new, send scoreboard create packets
                scoreboardManager.sendObjectiveCreate(player, objective)
                scoreboardManager.sendScoreChanges(player, objective, view.createChanges(contentItems))
            } else {
                // otherwise, update scores that the player should already have
                scoreboardManager.sendScoreChanges(player, objective, changes)
            }
        }

        val playerUuids = players.map { it.uuid }.toSet()
        for (removedPlayerUuid in view.players - playerUuids) {
            // if a player is removed, send a delete packet
            val removedPlayer = playerManager.getPlayer(removedPlayerUuid) ?: continue
            scoreboardManager.sendObjectiveDelete(removedPlayer, objective)
        }

        view.players = playerUuids
    }

    fun clearScoreboards() {
        for (view in scoreboardViews.values) {
            val objective = scoreboardManager.createDummyObjective(view.objectiveName)
            for (playerId in view.players) {
                val player = playerManager.getPlayer(playerId) ?: continue
                scoreboardManager.sendObjectiveDelete(player, objective)
            }
            scoreboardManager.removeObjective(objective)
        }
        scoreboardViews.clear()
    }
}