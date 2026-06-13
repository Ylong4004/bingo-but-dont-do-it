package me.jfenn.bingo.common.map

import me.jfenn.bingo.common.NBT_BINGO_CARD
import me.jfenn.bingo.common.NBT_BINGO_IGNORE
import me.jfenn.bingo.common.NBT_BINGO_KEEP
import me.jfenn.bingo.common.NBT_BINGO_VANISH
import me.jfenn.bingo.common.game.GameOverService
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.team.BingoTeam
import me.jfenn.bingo.common.team.TeamService
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.common.utils.formatString
import me.jfenn.bingo.generated.StringKey
import me.jfenn.bingo.platform.IMapService
import me.jfenn.bingo.platform.item.IItemStack
import me.jfenn.bingo.platform.item.IItemStackFactory
import net.minecraft.util.Formatting
import java.time.format.DateTimeFormatter

internal class MapItemService(
    private val state: BingoState,
    private val itemStackFactory: IItemStackFactory,
    private val mapService: IMapService,
    private val textProvider: TextProvider,
    private val gameOverService: GameOverService,
    private val teamService: TeamService,
) {

    fun createPreviewMapItem(): IItemStack {
        val previewMap = state.getPreviewMap(mapService)

        val item = itemStackFactory.createFilledMap().apply {
            mapId = previewMap.mapId
            setDisplay(
                name = textProvider.string(StringKey.CardPreviewCard).formatted(Formatting.ITALIC),
                lore = null,
            )
            setHideFlags(255)
            addCustomTag(NBT_BINGO_IGNORE)
            addCustomTag(NBT_BINGO_VANISH)
            addCustomTag(NBT_BINGO_KEEP)
            addCustomTag(NBT_BINGO_CARD)
        }

        return item
    }

    fun isPreviewMapItem(stack: IItemStack): Boolean {
        val previewMap = state.getPreviewMap(mapService)

        return stack.asFilledMap()?.mapId == previewMap.mapId
    }

    private val timeFormat = DateTimeFormatter.ofPattern("H:mm dd-MM-yyyy")

    fun createMementoMapItem(team: BingoTeam): IItemStack {
        val teamMap = teamService.getTeamMap(team)
        val gameInfo = state.gameOverInfo ?: return itemStackFactory.emptyStack

        val item = itemStackFactory.createFilledMap().apply {
            mapId = teamMap.mapId
            mapColor = team.textColor.colorValue
            setDisplay(
                name = textProvider.string(
                    when {
                        team.winner != null -> StringKey.CardTeamCardMementoWon
                        else -> StringKey.CardTeamCardMementoLost
                    }
                ).formatted(team.textColor, Formatting.ITALIC),
                lore = listOfNotNull(
                    team.getName(textProvider, playerName = true, symbol = true)
                        .copy()
                        .resetStyle(),
                    textProvider.empty(),
                    gameOverService.getTitle(gameInfo).copy().formatted(Formatting.WHITE),
                    gameOverService.getMessage(gameInfo),
                    state.ingameDuration()?.formatString()?.let { textProvider.literal(it) },
                    state.startedAt?.let { textProvider.literal(timeFormat.format(it)) },
                ),
            )
            setHideFlags(255)
            addCustomTag(NBT_BINGO_IGNORE)
        }

        return item
    }

    fun createMapItem(team: BingoTeam): IItemStack {
        val teamMap = teamService.getTeamMap(team)

        val item = itemStackFactory.createFilledMap().apply {
            mapId = teamMap.mapId
            mapColor = team.textColor.colorValue
            setDisplay(
                name = textProvider.string(StringKey.CardTeamCard, team.getSimpleName())
                    .formatted(team.textColor, Formatting.ITALIC),
                lore = null,
            )
            setHideFlags(255)
            addCustomTag(NBT_BINGO_IGNORE)
            addCustomTag(NBT_BINGO_VANISH)
            addCustomTag(NBT_BINGO_KEEP)
            addCustomTag(NBT_BINGO_CARD)
        }

        return item
    }

    fun isMapTeamItem(stack: IItemStack, team: BingoTeam): Boolean {
        val teamMap = teamService.getTeamMap(team)

        return isMapItem(stack) && stack.asFilledMap()?.mapId == teamMap.mapId
    }

    /**
     * Returns true if the item stack is any team's map card
     */
    fun isMapItem(stack: IItemStack): Boolean {
        return stack.asFilledMap() != null && stack.hasCustomTag(NBT_BINGO_CARD)
    }

}