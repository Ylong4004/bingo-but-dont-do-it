package me.jfenn.bingo.common.map

import me.jfenn.bingo.common.event.ScopedEvents
import me.jfenn.bingo.common.event.model.CardShuffledEvent
import me.jfenn.bingo.common.event.model.TeamWinnerEvent
import me.jfenn.bingo.common.scope.BingoComponent
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.state.GameState
import me.jfenn.bingo.platform.IPlayerManager
import me.jfenn.bingo.platform.event.IEventBus
import me.jfenn.bingo.platform.event.model.ReloadEvent
import me.jfenn.bingo.platform.event.model.TickEvent
import me.jfenn.bingo.platform.item.IItemStackFactory
import net.minecraft.server.MinecraftServer
import java.util.concurrent.CompletableFuture

/**
 * Handles re-rolling or generating new BINGO card layouts.
 */
internal class BingoMapController(
    events: ScopedEvents,
    eventBus: IEventBus,
    private val state: BingoState,
    private val playerManager: IPlayerManager,
    private val cardViewService: CardViewService,
    private val cardImageService: CardImageService,
    private val mapRenderService: MapRenderService,
    itemStackFactory: IItemStackFactory,
    server: MinecraftServer,
) : BingoComponent() {

    private fun updateCards() {
        // update all card map images
        if (state.state == GameState.PREGAME) {
            // if in pregame, update the preview card!
            cardViewService.updateCard(null)
        }

        for (team in state.getRegisteredTeams()) {
            cardViewService.updateCard(team)
        }
    }

    init {
        mapRenderService.validateItems(itemStackFactory.listItems(server))

        eventBus.register(TickEvent.Start) {
            cardImageService.sendNecessaryImages()
            updateCards()
        }

        events.onPlayerJoin { (player) ->
            cardImageService.clearPlayerState(player.uuid)
        }

        events.onPlayerChannelRegister { (player) ->
            cardViewService.sendUpdatePackets(player)
        }

        events.onChangeTeam { event ->
            cardViewService.sendClearDisplayPacket(event.player)
            cardViewService.sendUpdatePackets(event.player)

            // Send updates to spectators whenever a player changes team
            playerManager.getPlayers()
                .filter { cardViewService.isViewingSpectatorCards(it) }
                .forEach { cardViewService.sendUpdatePackets(it) }
        }

        eventBus.register(TeamWinnerEvent) { event ->
            event.team.players
                .mapNotNull { playerManager.getPlayer(it.uuid) }
                .forEach { cardViewService.sendUpdatePackets(it) }
        }

        eventBus.register(CardShuffledEvent) {
            cardViewService.sendCardShuffledPacket(it.cardId)
        }

        events.onChangeOptions {
            updateCards()
            playerManager.getPlayers().forEach { cardViewService.sendUpdatePackets(it) }
        }

        events.onStateChange { (_, to) ->
            val players = playerManager.getPlayers()

            if (to == GameState.PREGAME && state.isLobbyMode) {
                players.forEach { cardViewService.sendClearDisplayPacket(it) }
            }

            updateCards()
            for (player in playerManager.getPlayers()) {
                cardViewService.sendUpdatePackets(player)
            }
        }

        eventBus.register(ReloadEvent.After) {
            cardImageService.clearPlayerStates()
            mapRenderService.clearCache()
        }
    }
}