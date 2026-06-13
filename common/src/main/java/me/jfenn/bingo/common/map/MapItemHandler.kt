package me.jfenn.bingo.common.map

import me.jfenn.bingo.common.game.GameOverService
import me.jfenn.bingo.common.scope.BingoComponent
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.IPlayerManager
import me.jfenn.bingo.platform.dialog.IDialogManager
import me.jfenn.bingo.platform.event.IEventBus
import me.jfenn.bingo.platform.event.model.*
import me.jfenn.bingo.platform.item.IItemStackFactory
import net.minecraft.util.Hand
import java.util.*

internal class MapItemHandler(
    eventBus: IEventBus,
    private val playerManager: IPlayerManager,
    private val dialogManager: IDialogManager,
    private val mapItemService: MapItemService,
    private val itemStackFactory: IItemStackFactory,
    private val cardViewService: CardViewService,
    private val gameOverService: GameOverService,
    private val state: BingoState,
) : BingoComponent() {

    // set of players for which the bingo map item was used in the current tick
    private val usedBingoMap = mutableSetOf<UUID>()
    // set of players for which *any* item was used in the current tick
    private val usedAnyItem = mutableSetOf<UUID>()

    private val itemCooldown = mutableMapOf<UUID, Int>()

    private fun showInventory(player: IPlayerHandle) {
        state.gameOverInfo?.let { gameOverInfo ->
            val packet = gameOverService.createPacket(
                player = player,
                info = gameOverInfo,
                isUpdate = false,
            )
            val dialog = gameOverService.createDialog(packet)
            if (dialog != null) {
                dialogManager.showDialog(player, dialog)
                return
            }
        }

        cardViewService.openCard(player)
    }

    init {
        eventBus.register(UseItemEvent) { event ->
            val player = event.player.player
            val hand = event.hand
            val itemStack = player.getStackInHand(hand)
                ?.let { itemStackFactory.forStack(it) }
                ?: return@register null

            usedAnyItem.add(player.uuid)

            // if the item is the player's bingo map, open the item details chest
            if (mapItemService.isMapItem(itemStack)) {
                val otherItemStack = player.getStackInHand(when (hand) {
                    Hand.MAIN_HAND -> Hand.OFF_HAND
                    else -> Hand.MAIN_HAND
                }).let { itemStackFactory.forStack(it) }

                if (hand == Hand.OFF_HAND && !otherItemStack.isEmpty)
                    return@register ActionResult.Pass(itemStack)

                if (hand == Hand.MAIN_HAND && event.player.canUseItem(otherItemStack))
                    return@register ActionResult.Pass(itemStack)

                usedBingoMap.add(player.uuid)
                return@register ActionResult.Success(itemStack)
            }

            // if the item *can* be used, and *isn't* a bingo map, it should prevent the bingo map from being used
            if (usedBingoMap.contains(player.uuid) && event.player.canUseItem(itemStack)) {
                // println("Removing; item used ($hand, with ${itemStack.translationKey})")
                usedBingoMap.remove(player.uuid)
            }

            ActionResult.Pass(itemStack)
        }

        eventBus.register(UseBlockEvent) { event ->
            val player = event.player
            usedAnyItem.add(player.uuid)

            val itemStack = player.player.getStackInHand(event.hand)
                ?.let { itemStackFactory.forStack(it) }
                ?: return@register null

            if (
                usedBingoMap.contains(player.uuid)
                && !mapItemService.isMapItem(itemStack)
                && !itemStack.isEmpty
            ) {
                // println("Removing; block used ($hand, with ${itemStack.translationKey})")
                usedBingoMap.remove(player.uuid)
            }

            ActionResult.Pass(Unit)
        }

        eventBus.register(UseEntityEvent) { event ->
            val player = event.player
            usedAnyItem.add(player.uuid)

            val itemStack = player.player.getStackInHand(event.hand)
                ?.let { itemStackFactory.forStack(it) }
                ?: return@register null

            if (
                usedBingoMap.contains(player.uuid)
                && !mapItemService.isMapItem(itemStack)
                && !itemStack.isEmpty
            ) {
                // println("Removing; entity used")
                usedBingoMap.remove(player.uuid)
            }

            ActionResult.Pass(Unit)
        }

        eventBus.register(TickEvent.Start) {
            for (player in playerManager.getPlayers()) {
                // ensure the player waits at least 10 ticks before using the map
                // - if the use button is held, this will continually update the cooldown, and never exit this state until released
                val isInCooldown = it.ticks < (itemCooldown[player.uuid] ?: 0) + 10

                if (usedBingoMap.contains(player.uuid) && !isInCooldown && !player.isSneaking) {
                    showInventory(player)
                }

                if (usedAnyItem.contains(player.uuid) || player.player.itemUseTimeLeft > 0)
                    itemCooldown[player.uuid] = it.ticks
            }

            usedBingoMap.clear()
            usedAnyItem.clear()
        }
    }

}