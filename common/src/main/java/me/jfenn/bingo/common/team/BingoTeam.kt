package me.jfenn.bingo.common.team

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import me.jfenn.bingo.common.chat.CoordsCommand
import me.jfenn.bingo.common.map.BingoMap
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.common.utils.BlockPositionType
import me.jfenn.bingo.common.utils.FormattingSerializer
import me.jfenn.bingo.generated.StringKey
import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.IServerWorld
import me.jfenn.bingo.platform.block.BlockPosition
import me.jfenn.bingo.platform.player.PlayerProfile
import me.jfenn.bingo.platform.text.HoverAction
import me.jfenn.bingo.platform.text.IText
import me.jfenn.bingo.platform.text.ITextSerialized
import me.jfenn.bingo.platform.utils.UuidAsString
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Formatting
import java.time.Instant
import java.util.concurrent.CompletableFuture

@Serializable
@JvmInline
value class BingoTeamKey(
    val id: String,
) {
    val label get() = id.removePrefix("bingo_")
}

@Serializable
data class BingoTeam(
    val id: String,
    var name: ITextSerialized,
    val shouldFormatName: Boolean,
    val symbol: String?,
    @Serializable(with = FormattingSerializer::class)
    var textColor: Formatting,
    val isTemporary: Boolean = false,
    var cardId: UuidAsString? = null,
    var currentScore: TeamScore = TeamScore.ZERO,
    /**
     * Used when winCondition=ReplaceItems to track scored lines/items that no longer exist on the card
     */
    var persistentScore: TeamScore = TeamScore.ZERO,
    var winner: TeamWinner? = null,
    val completedCards: MutableList<TeamCompletedCard> = mutableListOf(),
    var spawnpoint: BlockPositionType? = null,
    var chestSpawnpoint: BlockPositionType? = null,
    val chatCoordinates: MutableList<CoordsCommand.CoordinatesEntry> = mutableListOf(),
    var map: BingoMap? = null,
    val players: MutableSet<PlayerProfile> = mutableSetOf(),
) {

    @Transient
    var scoreUpdatedAt: Instant = Instant.MIN

    /**
     * Creates a copy of the team that does not share any mutable
     * references with the original
     */
    fun newInstance(): BingoTeam {
        return copy(
            completedCards = mutableListOf(),
            chatCoordinates = mutableListOf(),
            players = mutableSetOf(),
        )
    }

    @Transient
    var spawnpointFuture: CompletableFuture<BlockPosition>? = null

    @Transient
    var spawnpointTicket: IServerWorld.IChunkTicketHandle? = null

    val key get() = BingoTeamKey(id)

    val mapColor get() = when (textColor) {
        // gray is indistinguishable from the preview card map item
        Formatting.GRAY -> Formatting.DARK_GRAY
        else -> textColor
    }

    val singlePlayer get() = players.takeIf { it.size == 1 }?.first()

    val score get() = currentScore + persistentScore

    val totalScore: TeamScore get() {
        val containsScore = completedCards.any { it.card.id == cardId }
        val scores = completedCards.map { it.score } + (if (containsScore) emptyList() else listOf(score))
        return TeamScore(
            cards = score.cards,
            lines = scores.sumOf { it.lines },
            items = scores.sumOf { it.items },
        )
    }

    fun isWinner(): Boolean = this.winner != null

    fun isPlaying() = !isWinner()

    fun includesPlayer(player: IPlayerHandle): Boolean {
        return players.any { it.uuid == player.uuid }
    }

    fun includesPlayer(player: ServerPlayerEntity): Boolean {
        return players.any { it.uuid == player.uuid }
    }

    fun getSimpleName() = name.copy()

    fun getSymbolText(textProvider: TextProvider): IText? {
        return players.takeIf { it.size == 1 }
            ?.firstOrNull()
            ?.let { textProvider.player(it.uuid)?.formatted(Formatting.WHITE)?.append(" ") }
            ?: symbol?.let { textProvider.literal("$it ") }
    }

    fun getName(
        textProvider: TextProvider,
        playerName: Boolean = false,
        symbol: Boolean = playerName && players.size == 1,
        bracketed: Boolean = true,
        teamNameKey: StringKey? = StringKey.TeamName,
    ): IText {
        val teamName = (if (playerName) singlePlayer?.let { textProvider.literal(it.name) } else null)
            ?: (if (shouldFormatName && teamNameKey != null) textProvider.string(teamNameKey, getSimpleName()) else null)
            ?: getSimpleName()

        val teamSymbol = getSymbolText(textProvider)

        return teamName
            .let { if (symbol && teamSymbol != null) textProvider.empty().append(teamSymbol).append(it) else it }
            .formatted(this.textColor)
            .let { if (bracketed) it.bracketed() else it }
            .also { t ->
                t.setHoverEvent(
                    HoverAction.ShowText(
                        textProvider.joinText(
                            list = players.map { textProvider.literal(it.name) },
                            separator = textProvider.literal("\n"),
                        )
                    )
                )
            }
    }

    fun countCards(): Int = completedCards.count { it.isWinner }

    companion object {
        fun fromPreset(key: BingoTeamKey, preset: BingoTeamPreset): BingoTeam {
            return BingoTeam(
                id = key.id,
                name = preset.name,
                shouldFormatName = preset.shouldFormatName,
                symbol = preset.symbol,
                textColor = preset.color,
            )
        }
    }

}