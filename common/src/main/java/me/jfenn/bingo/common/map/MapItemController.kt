package me.jfenn.bingo.common.map

import me.jfenn.bingo.common.config.BingoConfig
import me.jfenn.bingo.common.event.ScopedEvents
import me.jfenn.bingo.common.scope.BingoComponent
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.state.GameState
import me.jfenn.bingo.common.team.BingoTeam
import me.jfenn.bingo.common.team.TeamService
import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.IPlayerManager
import me.jfenn.bingo.platform.event.IEventBus
import me.jfenn.bingo.platform.event.model.TickEvent

internal class MapItemController(
    private val state: BingoState,
    private val config: BingoConfig,
    private val cardViewService: CardViewService,
    private val mapItemService: MapItemService,
    private val playerManager: IPlayerManager,
    private val teamService: TeamService,
    events: ScopedEvents,
    eventBus: IEventBus,
) : BingoComponent() {

    private fun removePreviewCardItem(player: IPlayerHandle) {
        for ((slot, item) in player.allInventorySlots()) {
            if (mapItemService.isPreviewMapItem(item))
                player.removeStack(slot)
        }
    }

    /**
     * Ensure that the player has their team's bingo map card.
     * Has no effect if the player already has a card
     */
    private fun giveMapCardItem(player: IPlayerHandle, team: BingoTeam?) {
        if (cardViewService.supportsCardHud(player)) return

        val previousSlot = player.allInventorySlots()
            .find { (_, stack) -> mapItemService.isMapItem(stack) }
            ?.first

        val hasMap = player.allHeldStackViews()
            .filter { mapItemService.isMapItem(it.stack) }
            .count { view ->
                val isItemValid = when (team) {
                    null -> mapItemService.isPreviewMapItem(view.stack)
                    else -> mapItemService.isMapTeamItem(view.stack, team)
                }
                if (isItemValid) {
                    view.mutate { it.count = 1 }
                    true
                } else {
                    // if the player has another team's map item, remove it!
                    view.mutate { it.count = 0 }
                    false
                }
            } > 0

        if (!hasMap) {
            // give the player a new map item
            val mapItem = when (team) {
                null -> mapItemService.createPreviewMapItem()
                else -> mapItemService.createMapItem(team)
            }
            previousSlot?.let { player.setStack(it, mapItem) }
                ?: player.giveItemStack(mapItem)
        }
    }

    /**
     * Ensure that the player has every team's bingo map card
     * in their hotbar slots. Indiscriminately replaces any existing
     * hotbar items.
     */
    private fun giveSpectatorMapItems(player: IPlayerHandle) {
        if (cardViewService.supportsCardHud(player)) return

        state.getRegisteredTeams()
            // don't give a player more map items than they can hold
            .take(9*4)
            .forEachIndexed { i, team ->
                val hasMap = player.allHeldStacks()
                    .any { mapItemService.isMapTeamItem(it, team) }

                if (!hasMap) {
                    val stack = mapItemService.createMapItem(team)
                    if (state.isLobbyMode) {
                        // if isLobbyMode=true, then it's okay to replace hotbar items...
                        player.setStack(i, stack)
                    } else {
                        // otherwise, give the cards without overwriting their inventory
                        player.giveItemStack(stack)
                    }
                }
            }
    }

    /**
     * Remove all bingo map items from the player
     */
    private fun removeAllMapItems(player: IPlayerHandle) {
        player.allHeldStackViews()
            .filter { mapItemService.isMapItem(it.stack) }
            .forEach { view -> view.mutate { it.count = 0 } }
    }

    init {
        events.onEnter(GameState.POSTGAME) {
            for (player in playerManager.getPlayers()) {
                // remove any existing maps in the player's inventory
                removeAllMapItems(player)

                // give all maps for active teams to the player
                if (state.isLobbyMode && !cardViewService.supportsCardHud(player)) {
                    giveSpectatorMapItems(player)
                }
            }

            // if isLobbyMode=false, give the players a "memento card" after each game
            if (!state.isLobbyMode && config.giveMementoInSurvival) {
                for (player in playerManager.getPlayers()) {
                    val team = teamService.getPlayerTeam(player) ?: continue
                    val mapItem = mapItemService.createMementoMapItem(team)
                    player.giveItemStack(mapItem)
                }
            }
        }

        eventBus.register(TickEvent.Start) {
            for (player in playerManager.getPlayers()) {
                val playerTeam = teamService.getPlayerTeam(player)
                val canHaveCards = player.isAlive && !player.isSpectator

                if (canHaveCards) {
                    if (cardViewService.isViewingSpectatorCards(player)) {
                        if (state.isLobbyMode)
                            giveSpectatorMapItems(player)
                    } else if (playerTeam != null && cardViewService.isViewingTeamCard(player, playerTeam)) {
                        // give player a new map, if they don't have one?
                        giveMapCardItem(player, playerTeam)
                    }
                }

                if (canHaveCards && cardViewService.isViewingPreviewCard(player)) {
                    giveMapCardItem(player, null)
                } else {
                    removePreviewCardItem(player)
                }

                // If the player is using the client-side hud, make sure they don't have any map items
                // (this might be false when the player is first connecting)
                if (cardViewService.supportsCardHud(player))
                    removeAllMapItems(player)
            }
        }
    }
}