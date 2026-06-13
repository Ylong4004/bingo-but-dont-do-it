package me.jfenn.bingo.client.common.state

import me.jfenn.bingo.client.common.hud.BingoCardColors
import me.jfenn.bingo.client.common.hud.BingoMessageRenderer
import me.jfenn.bingo.client.platform.INativeImage
import me.jfenn.bingo.common.game.GameOverPacket
import me.jfenn.bingo.common.game.GameStatusPacket
import me.jfenn.bingo.common.menu.tooltips.TooltipPacket
import me.jfenn.bingo.common.ready.ReadyUpdatePacket
import me.jfenn.bingo.common.scoring.GameMessagePacket
import me.jfenn.bingo.common.team.BingoTeamKey
import me.jfenn.bingo.platform.text.IText
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

internal class BingoHudState(
    var now: Instant = Instant.now(),
    var cards: MutableMap<BingoTeamKey?, ClientCardBase> = mutableMapOf(),
    var selectedTeam: BingoTeamKey? = null,
    var gameStatus: GameStatusPacket = GameStatusPacket.DEFAULT,
    var gameOver: GameOver? = null,
    val messages: MutableList<ScoreMessage> = mutableListOf(),
    val pastMessages: MutableList<ScoreMessage> = mutableListOf(),
    val images: MutableMap<String, INativeImage> = ConcurrentHashMap(),
    var tooltip: TooltipPacket? = null,
    var tooltipStartedAt: Instant? = null,
    var ready: ReadyUpdatePacket? = null,
    var cardColors: BingoCardColors = BingoCardColors(),
) {
    fun reset() {
        selectedTeam = null
        resetGameOver()
        tooltip = null
        tooltipStartedAt = null
        ready = null
    }

    fun resetGameOver() {
        gameOver?.cards?.forEach { card ->
            // If the card is only used in the gameOver screen, close it!
            if (!cards.containsValue(card))
                card.close()
        }
        gameOver = null
    }

    fun clearMessages() {
        messages.clear()
        pastMessages.clear()
    }

    fun resetAll() {
        reset()
        clearMessages()
        gameStatus = GameStatusPacket.DEFAULT
        cards.values.forEach { it.close() }
        cards = mutableMapOf()

        val clearedImages = images.values.toList()
        images.clear()
        for (clearedImage in clearedImages) {
            clearedImage.close()
        }
    }

    class GameOver(
        var packet: GameOverPacket,
        var cards: List<ClientCardBase>,
        var tab: GameOverPacket.EndScreenTab = packet.defaultTab,
        var isFirstOpen: Boolean = true,
    )

    data class ScoreMessage(
        val createdAt: Instant,
        val packet: GameMessagePacket,
        val messageLines: List<IText> = listOf(packet.message),
        val height: Int = BingoMessageRenderer.ITEM_HEIGHT,
    )
}