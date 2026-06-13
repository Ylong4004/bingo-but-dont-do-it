package me.jfenn.bingo.common.scoring

import me.jfenn.bingo.common.config.PlayerSettingsService
import me.jfenn.bingo.common.event.packet.ServerPacketEvents
import me.jfenn.bingo.common.map.CardTileImage
import me.jfenn.bingo.common.options.BingoOptions
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.state.GameState
import me.jfenn.bingo.common.team.TeamService
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.common.utils.formatHHMMSS
import me.jfenn.bingo.generated.StringKey
import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.IPlayerManager
import me.jfenn.bingo.platform.text.IText
import net.minecraft.util.Formatting

internal class GameMessageService(
    private val packets: ServerPacketEvents,
    private val options: BingoOptions,
    private val state: BingoState,
    private val text: TextProvider,
    private val playerSettingsService: PlayerSettingsService,
    private val playerManager: IPlayerManager,
    private val teamService: TeamService,
) {

    private fun formatWithTime(gameMessage: GameMessage, message: IText): IText {
        return text.empty()
            .append(
                text.literal(gameMessage.timeElapsed.formatHHMMSS())
                    .bracketed()
                    .formatted(Formatting.GRAY)
            )
            .append(" ")
            .append(message)
    }

    fun createMessagePacket(
        player: IPlayerHandle,
        message: GameMessage,
    ) = when (message) {
        is GameMessage.ItemScored -> createItemScoredMessage(player, message)
        is GameMessage.LineScored -> createLineScoredMessage(message)
        is GameMessage.CardCompleted -> createCardCompletedMessage(message)
        is GameMessage.LeadingTeam -> createLeadingTeamMessage(message)
    }

    private fun createItemScoredMessage(
        player: IPlayerHandle,
        message: GameMessage.ItemScored,
    ): GameMessagePacket? {
        val team = state.teams[message.team] ?: return null
        val card = state.cards.find { it.id == message.cardId } ?: return null

        val isOnTeamOrSpectator = team.includesPlayer(player) || !teamService.isPlaying(player)
        // if playing in hidden items mode, don't reveal the item to the other teams!
        val isItemHidden = card.options.isHiddenItemsMode && !isOnTeamOrSpectator && state.state != GameState.POSTGAME
        val itemText = when {
            isItemHidden -> text.literal("*****").formatted(Formatting.OBFUSCATED)
            else -> message.itemName
        }.bracketed()

        val chatMessage = text.string(
            if (message.isLost) StringKey.GameMessageLostItem else StringKey.GameMessageCapturedItem,
            when {
                message.player != null -> {
                    text.empty()
                        .append(team.getSymbolText(text) ?: text.empty())
                        .append(message.player.name)
                        .formatted(team.textColor)
                }
                else -> team.getName(text, playerName = true, bracketed = false)
            },
            itemText,
        ).let { formatWithTime(message, it) }

        return GameMessagePacket(
            id = message.id,
            timeElapsed = message.timeElapsed,
            team = team.key,
            image = message.image.takeIf { !isItemHidden } ?: CardTileImage.EMPTY,
            imageList = message.imageList.takeIf { !isItemHidden }.orEmpty(),
            decoration = message.decoration.takeIf { !isItemHidden },
            messageType = ScoreMessagePacket.MessageType.ITEM_SCORED,
            message = chatMessage,
        )
    }

    private fun createLineScoredMessage(
        message: GameMessage.LineScored,
    ) : GameMessagePacket? {
        val team = state.teams[message.team] ?: return null
        val teamName = team.getName(text, playerName = true)
        val chatMessage = text.string(StringKey.GameMessageScoredLine, teamName, text.lineCount(message.lines))
            .formatted(Formatting.YELLOW)
            .let { formatWithTime(message, it) }

        return GameMessagePacket(
            id = message.id,
            timeElapsed = message.timeElapsed,
            team = team.key,
            decoration = null,
            messageType = ScoreMessagePacket.MessageType.LINE_SCORED,
            message = chatMessage,
        )
    }

    private fun createCardCompletedMessage(
        message: GameMessage.CardCompleted,
    ) : GameMessagePacket? {
        val team = state.teams[message.team] ?: return null
        val string = if (message.isAutoWin) StringKey.GameMessageCompletedCardByStalemate else StringKey.GameMessageCompletedCard
        val chatMessage = text.string(string, team.getName(text, playerName = true))
            .formatted(Formatting.YELLOW)
            .let { formatWithTime(message, it) }

        return GameMessagePacket(
            id = message.id,
            timeElapsed = message.timeElapsed,
            team = team.key,
            decoration = null,
            messageType = ScoreMessagePacket.MessageType.CARD_COMPLETED,
            message = chatMessage,
        )
    }

    private fun createLeadingTeamMessage(
        message: GameMessage.LeadingTeam
    ) : GameMessagePacket? {
        val team = state.teams[message.team] ?: return null
        val teamName = team.getName(text, playerName = true)
        val scoreText = when {
            message.cards != null -> text.cardCount(message.cards)
            message.lines != null -> text.lineCount(message.lines)
            message.items != null -> text.itemCount(message.items)
            else -> error("Invalid leading team GameMessage")
        }
        val chatMessage = when {
            message.isTied && options.showCompletedItems -> {
                text.string(StringKey.GameMessageLeadingTiedWithScore, teamName, scoreText)
            }
            message.isTied -> text.string(StringKey.GameMessageLeadingTied, teamName)
            options.showCompletedItems -> {
                text.string(StringKey.GameMessageLeadingWithScore, teamName, scoreText)
            }
            else -> text.string(StringKey.GameMessageLeading, teamName)
        }
            .formatted(Formatting.YELLOW)
            .let { formatWithTime(message, it) }

        return GameMessagePacket(
            id = message.id,
            timeElapsed = message.timeElapsed,
            team = team.key,
            decoration = null,
            messageType = ScoreMessagePacket.MessageType.LEADING_TEAM,
            message = chatMessage,
        )
    }

    fun isEnabled(
        player: IPlayerHandle,
        message: GameMessage,
    ): Boolean {
        val playerTeam = teamService.getPlayerTeam(player)
        val isOnTeamOrSpectator = playerTeam == null || playerTeam.key == message.team
        return when (message) {
            is GameMessage.ItemScored -> (options.showCompletedItems || isOnTeamOrSpectator) && playerSettingsService.getPlayer(player).itemMessages
            is GameMessage.LineScored -> (options.showCompletedLines || isOnTeamOrSpectator) && playerSettingsService.getPlayer(player).scoreMessages
            is GameMessage.CardCompleted -> playerSettingsService.getPlayer(player).scoreMessages
            is GameMessage.LeadingTeam -> options.showLeadingTeam && playerSettingsService.getPlayer(player).leadingMessages
        }
    }

    fun sendMessagePacket(
        player: IPlayerHandle,
        packet: GameMessagePacket,
    ) {
        when {
            packets.gameMessageV1.send(player, packet) -> {}
            !packet.isUpdate -> {
                val isViewerOnTeam = when (packet.messageType) {
                    ScoreMessagePacket.MessageType.ITEM_SCORED -> {
                        state.teams[packet.team]?.includesPlayer(player) != false ||
                                !teamService.isPlaying(player)
                    }
                    ScoreMessagePacket.MessageType.LINE_SCORED,
                    ScoreMessagePacket.MessageType.CARD_COMPLETED,
                    ScoreMessagePacket.MessageType.LEADING_TEAM -> true
                }
                val scoreMessagePacket = ScoreMessagePacket.fromGameMessage(
                    gameMessagePacket = packet,
                    isViewerOnTeam = isViewerOnTeam
                )
                when {
                    packets.scoreMessageV3.send(player, scoreMessagePacket) -> {}
                    packets.scoreMessageV2.send(player, scoreMessagePacket) -> {}
                    packets.scoreMessageV1.send(player, scoreMessagePacket) -> {}
                    else -> player.sendMessage(packet.message)
                }
            }
        }
    }

    fun sendClearMessagesPacket(player: IPlayerHandle) {
        packets.gameMessageClearV1.send(player, GameMessageClearPacket)
    }

    fun addGameMessage(
        message: GameMessage
    ) {
        state.gameMessages.add(message)

        playerManager.getPlayers()
            .filter { isEnabled(it, message) }
            .forEach {
                createMessagePacket(
                    player = it,
                    message = message,
                )?.let { packet ->
                    sendMessagePacket(
                        player = it,
                        packet = packet
                    )
                }
            }
    }

}