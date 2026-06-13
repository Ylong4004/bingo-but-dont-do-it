package me.jfenn.bingo.integrations.voice

import me.jfenn.bingo.common.event.ScopedEvents
import me.jfenn.bingo.common.event.model.TeamWinnerEvent
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.state.GameState
import me.jfenn.bingo.common.team.BingoTeam
import me.jfenn.bingo.common.team.TeamService
import me.jfenn.bingo.common.utils.formatTitle
import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.IPlayerManager
import me.jfenn.bingo.platform.config.IConfigManager
import me.jfenn.bingo.platform.config.read
import me.jfenn.bingo.platform.config.write
import me.jfenn.bingo.platform.event.IEventBus

internal class VoiceGroupController(
    configManager: IConfigManager,
    private val playerManager: IPlayerManager,
    private val voiceApi: IVoiceApi,
    private val state: BingoState,
    private val teamService: TeamService,
    scopedEvents: ScopedEvents,
    eventBus: IEventBus,
) {

    companion object {
        private const val CONFIG_PATH = "yet-another-minecraft-bingo/voicechat.json"
    }

    val config: VoiceConfig = try {
        configManager.read(CONFIG_PATH)
    } catch (e: Throwable) {
        VoiceConfig()
    }.also {
        if (voiceApi.isInstalled()) {
            configManager.write(CONFIG_PATH, it)
        }
    }

    private var usingCombinedGroup: Boolean? = null
    private fun isUsingCombinedGroup(): Boolean {
        usingCombinedGroup?.let { return it }

        val isSingleplayerTeams = state.getRegisteredTeams().all { it.players.size <= 1 }
        usingCombinedGroup = isSingleplayerTeams && config.useCombinedGroupForSingleplayerTeams
        return isSingleplayerTeams
    }

    private var combinedGroup: IGroupHandle? = null
        get() = field ?: run {
            val settings = config.combinedGroup ?: return@run null
            field = voiceApi.createGroup(settings)
            field
        }


    private var spectatorsGroup: IGroupHandle? = null
        get() = field ?: run {
            val settings = config.spectatorGroup ?: return@run null
            field = voiceApi.createGroup(settings)
            field
        }

    private val teamGroups = mutableMapOf<String, IGroupHandle?>()
    private fun createTeamGroup(team: BingoTeam): IGroupHandle? {
        return teamGroups.getOrPut(team.id) {
            val settings = config.teamGroups ?: return@getOrPut null
            val teamName = team.key.label.formatTitle()
            val name = when {
                settings.name.contains("%s") -> settings.name.replace("%s", teamName)
                else -> "${settings.name}: $teamName"
            }
            voiceApi.createGroup(settings.copy(name = name))
        }
    }

    private fun getGroup(team: BingoTeam?): IGroupHandle? {
        return when {
            state.state == GameState.PREGAME -> null
            state.state == GameState.POSTGAME -> combinedGroup
            config.useCombinedGroupAlways -> combinedGroup
            team == null -> spectatorsGroup
            isUsingCombinedGroup() -> combinedGroup
            // if the game is _not_ using a combined group, move winning teams to spectator
            team.isWinner() -> spectatorsGroup
            else -> createTeamGroup(team)
        }
    }

    private fun updateGroups(players: List<IPlayerHandle>) {
        for (player in players) {
            val team = teamService.getPlayerTeam(player)
            getGroup(team)?.addPlayer(player)
        }
    }

    private fun onStateChange() {
        // reset usingCombinedGroup on state change or between rounds
        usingCombinedGroup = null

        val playersToUpdate = when {
            state.isLobbyMode -> playerManager.getPlayers()
            else -> playerManager.getPlayers().filter { teamService.isPlaying(it) }
        }
        updateGroups(playersToUpdate)
    }

    init {
        scopedEvents.onEnter(GameState.STARTING) {
            onStateChange()
        }

        scopedEvents.onEnter(GameState.POSTGAME) {
            onStateChange()
            // Clean up the team groups, which should now be unused
            teamGroups.values.forEach { it?.close() }
            teamGroups.clear()
        }

        scopedEvents.onEnter(GameState.PREGAME) {
            // Remove players from all groups
            // (as voicechat never deletes groups that have players in them)
            playerManager.getPlayers().forEach { player ->
                combinedGroup?.removePlayer(player)
                spectatorsGroup?.removePlayer(player)
                teamGroups.values.forEach { it?.removePlayer(player) }
            }

            usingCombinedGroup = null
            combinedGroup?.close()
            combinedGroup = null
            spectatorsGroup?.close()
            spectatorsGroup = null
            teamGroups.values.forEach { it?.close() }
            teamGroups.clear()
        }

        scopedEvents.onChangeTeam { (player) ->
            playerManager.getPlayer(player.uuid)
                ?.let { updateGroups(listOf(it)) }
        }

        scopedEvents.onPlayerJoin { (player) ->
            updateGroups(listOf(player))
        }

        eventBus.register(TeamWinnerEvent) { (team) ->
            team.players
                .mapNotNull { playerManager.getPlayer(it.uuid) }
                .let { updateGroups(it) }
        }
    }
}
