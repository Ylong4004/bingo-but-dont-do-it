package me.jfenn.bingo.common.chat

import me.jfenn.bingo.common.config.BingoConfig
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.team.TeamService
import me.jfenn.bingo.platform.event.IEventBus
import me.jfenn.bingo.platform.event.model.AllowChatMessageEvent

internal class ChatMessageController(
    eventBus: IEventBus,
    private val chatMessageService: ChatMessageService,
    private val state: BingoState,
    private val config: BingoConfig,
    private val teamService: TeamService,
) {
    init {
        // override default chat to only send team-specific messages
        eventBus.register(AllowChatMessageEvent) { (message, player) ->
            if (chatMessageService.bypassAllowMessageCheck) return@register true

            val shouldTeamChat = when {
                teamService.isPlaying(player) -> config.chat.defaultToTeamChat
                else -> config.chat.defaultToSpectatorChat
            }

            if (state.state.isPlayingOrCountdown && shouldTeamChat) {
                val sent = chatMessageService.sendTeamMessage(message, player)
                !sent
            } else {
                true
            }
        }
    }
}