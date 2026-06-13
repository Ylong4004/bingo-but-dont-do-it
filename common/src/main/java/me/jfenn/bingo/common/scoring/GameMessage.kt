package me.jfenn.bingo.common.scoring

import kotlinx.serialization.Serializable
import me.jfenn.bingo.common.map.CardTile
import me.jfenn.bingo.common.map.CardTileImage
import me.jfenn.bingo.common.team.BingoTeamKey
import me.jfenn.bingo.common.utils.DurationType
import me.jfenn.bingo.platform.player.PlayerProfile
import me.jfenn.bingo.platform.text.ITextSerialized
import me.jfenn.bingo.platform.utils.UuidAsString
import java.util.*

@Serializable
sealed class GameMessage {

    val id: UuidAsString = UUID.randomUUID()
    abstract val timeElapsed: DurationType
    abstract val team: BingoTeamKey?

    @Serializable
    data class ItemScored(
        override val timeElapsed: DurationType,
        override val team: BingoTeamKey,
        val cardId: UuidAsString,
        val image: CardTileImage,
        val imageList: List<CardTileImage>,
        val decoration: CardTile.Decoration?,
        val itemName: ITextSerialized,
        val player: PlayerProfile?,
        val isLost: Boolean,
    ) : GameMessage()

    @Serializable
    data class LineScored(
        override val timeElapsed: DurationType,
        override val team: BingoTeamKey,
        val lines: Int,
    ) : GameMessage()

    @Serializable
    data class CardCompleted(
        override val timeElapsed: DurationType,
        override val team: BingoTeamKey,
        val isAutoWin: Boolean,
    ) : GameMessage()

    @Serializable
    data class LeadingTeam(
        override val timeElapsed: DurationType,
        override val team: BingoTeamKey,
        val cards: Int?,
        val lines: Int?,
        val items: Int?,
        val isTied: Boolean,
    ) : GameMessage()
}