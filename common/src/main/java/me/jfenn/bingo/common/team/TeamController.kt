package me.jfenn.bingo.common.team

import me.jfenn.bingo.common.BINGO_TEAM_PREFIX
import me.jfenn.bingo.common.event.ScopedEvents
import me.jfenn.bingo.common.event.model.TeamChangedEvent
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.state.GameState
import me.jfenn.bingo.platform.IPlayerManager
import me.jfenn.bingo.platform.ITeamManager
import me.jfenn.bingo.platform.event.IEventBus
import org.slf4j.Logger

internal class TeamController(
    private val teamService: TeamService,
    private val state: BingoState,
    private val playerManager: IPlayerManager,
    private val teamManager: ITeamManager,
    events: ScopedEvents,
    private val eventBus: IEventBus,
    private val log: Logger,
) {
    init {
        events.onEnter(GameState.PREGAME) {
            // clear the team of all online players when entering PREGAME
            // (so that they must rejoin a team for the next game)
            teamService.clearTeams()
        }

        events.onGameTick {
            // If a team is created/changed/removed, ensure that state.teams
            // stays up to date
            val teamIds = teamManager.listTeams()
                .plus(state.teams.values.map { it.id })
                .distinct()

            for (teamId in teamIds) {
                val mcTeam = teamManager.getTeam(teamId)
                val bingoTeam = state.teams.values
                    .find { it.id == teamId }

                if (mcTeam != null && bingoTeam != null) {
                    if (bingoTeam.players.isEmpty() && bingoTeam.isTemporary) {
                        log.info("[TeamController] Removing team '$teamId' because it no longer contains any players.")
                        continue
                    }

                    bingoTeam.name = mcTeam.displayName
                    bingoTeam.textColor = mcTeam.color
                } else if (mcTeam != null && state.isLobbyMode && !teamId.startsWith(BINGO_TEAM_PREFIX)) {
                    log.info("[TeamController] Creating a bingo team for '$teamId'")
                    val newTeam = BingoTeam(
                        id = mcTeam.name,
                        name = mcTeam.displayName,
                        shouldFormatName = false,
                        symbol = null,
                        textColor = mcTeam.color,
                    )
                    state.registerTeam(newTeam)
                } else if (mcTeam == null && bingoTeam != null) {
                    // The team no longer exists; delete it
                    log.info("[TeamController] Removing bingo team for '$teamId'")
                    state.teams.remove(bingoTeam.key)
                }
            }

            for (player in playerManager.getPlayers()) {
                val playerTeam = teamService.getPlayerTeam(player)

                // If the player has switched teams, update the player UUID sets...
                // (this is necessary because teams could be changed without us knowing, using /team join)
                val playerProfile = player.profile
                val isTeamLeft = state.teams.values
                    .filter { it.key != playerTeam?.key }
                    .any { team ->
                        team.players.remove(playerProfile)
                    }

                val isTeamJoined = playerTeam?.players?.add(playerProfile) ?: false

                if (isTeamLeft || isTeamJoined) {
                    log.info("[TeamController] Manually updated teams for '${player.playerName}'")
                    val newTeam = teamService.getPlayerTeam(player)
                    eventBus.emit(TeamChangedEvent, TeamChangedEvent(player, newTeam))
                    state.playersJoinedIds.add(player.uuid)
                    state.playersSpectatingIds.remove(player.uuid)
                }
            }
        }
    }
}