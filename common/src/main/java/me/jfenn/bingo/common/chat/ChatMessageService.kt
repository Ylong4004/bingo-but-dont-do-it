package me.jfenn.bingo.common.chat

import me.jfenn.bingo.platform.text.IText
import me.jfenn.bingo.common.team.TeamService
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.generated.StringKey
import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.IPlayerManager
import me.jfenn.bingo.platform.commands.ISignedMessage

internal class ChatMessageService(
    private val playerManager: IPlayerManager,
    private val teamService: TeamService,
    private val text: TextProvider,
) {
    var bypassAllowMessageCheck = false

    fun sendGlobalMessage(
        signedMessage: ISignedMessage,
        sender: IPlayerHandle,
    ) {
        try {
            bypassAllowMessageCheck = true
            playerManager.broadcastChatMessage(signedMessage, sender)
        } finally {
            bypassAllowMessageCheck = false
        }
    }

    fun sendTeamMessage(
        signedMessage: ISignedMessage,
        sender: IPlayerHandle,
    ): Boolean {
        val team = teamService.getPlayerTeam(sender)
        val players = when (team) {
            null -> playerManager.getPlayers().filter { !teamService.isPlaying(it) }
            else -> playerManager.getPlayers().filter { team.includesPlayer(it) }
        }

        val teamName = team?.getSimpleName()
            ?.bracketed()
            ?.formatted(team.textColor)
            ?: text.string(StringKey.CommandTeammsgSpectators).bracketed()

        players.forEach { it.sendTeamMessage(signedMessage, sender, teamName) }

        return true
    }

    fun sendTeamMessage(
        message: IText,
        sender: IPlayerHandle,
    ): Boolean {
        val team = teamService.getPlayerTeam(sender) ?: return false

        val teamName = team.getSimpleName().bracketed().formatted(team.textColor)

        playerManager.getPlayers()
            .filter { team.includesPlayer(it) }
            .forEach { it.sendTeamMessage(message, sender, teamName) }

        return true
    }
}