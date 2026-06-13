package me.jfenn.bingo.impl

import me.jfenn.bingo.platform.text.IText
import me.jfenn.bingo.platform.ITeamHandle
import me.jfenn.bingo.platform.ITeamManager
import net.minecraft.scoreboard.Team
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Formatting

class TeamManager(
    private val server: MinecraftServer,
) : ITeamManager {

    override fun listTeams(): List<String> {
        return server.scoreboard.teamNames.toList()
    }

    override fun getTeam(id: String): ITeamHandle? {
        return server.scoreboard.getTeam(id)
            ?.let { TeamHandle(it) }
    }

    override fun createTeam(teamName: String, displayName: IText, color: Formatting): ITeamHandle {
        return TeamHandle(
            server.scoreboard.getTeam(teamName)
                ?: server.scoreboard.addTeam(teamName)
        ).also {
            it.displayName = displayName
            it.color = color
        }
    }

    override fun deleteTeam(teamName: String) {
        val team = server.scoreboard.getTeam(teamName)
        if (team != null) server.scoreboard.removeTeam(team)
    }

    override fun getPlayerTeam(player: ServerPlayerEntity): TeamHandle? {
        return player.server.scoreboard.getPlayerTeam(player.entityName)
            ?.let { TeamHandle(it) }
    }

    override fun setPlayerTeam(player: ServerPlayerEntity, team: ITeamHandle?) {
        require(team is TeamHandle?)

        if (team != null) {
            player.server.scoreboard.addPlayerToTeam(player.entityName, team.team)
        } else {
            // leave the player's current team (set to null)
            val currentTeam = getPlayerTeam(player) ?: return
            player.server.scoreboard.removePlayerFromTeam(player.entityName, currentTeam.team)
        }
    }
}

class TeamHandle(
    val team: Team
) : ITeamHandle {
    override val name: String
        get() = team.name

    override var displayName: IText
        get() = TextImpl(team.displayName.copy())
        set(value) { team.displayName = value.value }

    override var color: Formatting by team::color
}
