package me.jfenn.bingo.common.team

import me.jfenn.bingo.common.Permission
import me.jfenn.bingo.common.data.ScopedData
import me.jfenn.bingo.common.event.model.TeamChangedEvent
import me.jfenn.bingo.common.map.BingoMap
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.generated.StringKey
import me.jfenn.bingo.integrations.permissions.IPermissionsApi
import me.jfenn.bingo.platform.*
import me.jfenn.bingo.platform.event.IEventBus
import net.minecraft.util.Formatting
import org.slf4j.Logger
import kotlin.math.ceil

internal class TeamService(
    private val log: Logger,
    private val eventBus: IEventBus,
    private val state: BingoState,
    private val data: ScopedData,
    private val teamManager: ITeamManager,
    private val text: TextProvider,
    private val playerManager: IPlayerManager,
    private val mapService: IMapService,
    private val permissions: IPermissionsApi,
) {

    fun getPlayerTeam(player: IPlayerHandle): BingoTeam? {
        val team = teamManager.getPlayerTeam(player.player) ?: return null
        return state.teams[BingoTeamKey(team.name)]
    }

    fun isPlaying(player: IPlayerHandle): Boolean {
        return getPlayerTeam(player) != null
    }

    fun isTeamChosen(player: IPlayerHandle) = isPlaying(player) || state.playersSpectatingIds.contains(player.uuid)

    fun getTeamMap(team: BingoTeam): BingoMap {
        return team.map ?: run {
            val map = BingoMap(
                mapId = mapService.getNextMapId(),
            )

            log.info("Creating a map for team ${team.key.id} - mapId=${map.mapId}")
            team.map = map
            return@run map
        }
    }

    private fun createTeam(team: BingoTeam): ITeamHandle {
        return teamManager.createTeam(
            teamName = team.id,
            displayName = team.getSimpleName(),
            color = team.textColor,
        )
    }

    fun joinTeam(player: IPlayerHandle, teamTemplate: BingoTeam) {
        if (getPlayerTeam(player)?.key == teamTemplate.key) return
        val team = state.registerTeam(teamTemplate)
        val playerProfile = player.profile

        // Remove player from their existing team
        getPlayerTeam(player)?.players?.remove(playerProfile)

        state.playersSpectatingIds.remove(player.uuid)
        teamManager.setPlayerTeam(player.player, createTeam(team))
        team.players.add(playerProfile)
        player.sendMessage(
            text.string(StringKey.LobbyTeamChanged, team.getName(text))
                .formatted(Formatting.YELLOW)
        )

        eventBus.emit(TeamChangedEvent, TeamChangedEvent(player, team))
    }

    fun joinSpectators(player: IPlayerHandle) {
        val team = getPlayerTeam(player)

        if (team != null) {
            teamManager.setPlayerTeam(player.player, null)
            val removed = team.players.remove(player.profile)
            if (removed) {
                eventBus.emit(TeamChangedEvent, TeamChangedEvent(player, null))
            }
        }

        val addedToSpectators = state.playersSpectatingIds.add(player.uuid)
        if (addedToSpectators) {
            player.sendMessage(
                text.string(StringKey.LobbyTeamSpectators)
                    .formatted(Formatting.YELLOW)
            )
        }
    }

    private val extraTeamColors = arrayOf(
        Formatting.DARK_AQUA,
        Formatting.DARK_BLUE,
        Formatting.DARK_RED,
        Formatting.DARK_GREEN,
        Formatting.DARK_PURPLE,
    )

    fun shuffleTeams(numTeams: Int) {
        // Since teams are being shuffled, all existing teams should be cleared
        state.teams.clear()

        val teams = data.teamPresets.entries
            .shuffled()
            // Prefer shuffling onto teams that have the most players
            .sortedByDescending { state.teams[it.key]?.players?.size ?: 0 }
            .map { BingoTeam.fromPreset(it.key, it.value) }
            .toMutableList()

        // If numTeams is more than the default team list, create temporary teams until full
        for (i in teams.size..numTeams) {
            val temporaryTeam = BingoTeam(
                id = "temp_bingo_$i",
                name = text.string(StringKey.TeamName, "$i"),
                shouldFormatName = false,
                symbol = null,
                textColor = extraTeamColors.find { color ->
                    teams.none { it.textColor == color }
                } ?: run {
                    (data.teamPresets.values.map { it.color } + extraTeamColors).random()
                },
                isTemporary = true,
            )
            teams.add(temporaryTeam)
        }

        val players = playerManager.getPlayers()
            .filter { !state.playersSpectatingIds.contains(it.uuid) }
        val playersPerTeam = ceil(players.size / numTeams.coerceIn(1, teams.size).toDouble()).toInt()

        players.shuffled()
            .chunked(playersPerTeam)
            .forEachIndexed { i, teamPlayers ->
                val teamTemplate = teams[i % teams.size]

                for (teamPlayer in teamPlayers)
                    joinTeam(teamPlayer, teamTemplate)
            }
    }

    fun clearTeams() {
        for (team in data.teamPresets.keys + state.teams.keys) {
            teamManager.deleteTeam(team.id)
        }
        state.teams.clear()
    }

    fun notifyMissingTeam(player: IPlayerHandle) {
        val title = text.string(StringKey.CommandStartJoinATeam)
        val message = text.string(StringKey.CommandStartJoinATeamMessage, JoinCommand.JOIN_COMMAND)

        if (permissions.hasPermission(player, Permission.COMMAND_JOIN)) {
            player.sendTitle(title, message)
        }
    }

}