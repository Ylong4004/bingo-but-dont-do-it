package me.jfenn.bingo.common.spectator

import me.jfenn.bingo.common.card.EmptySlot
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.ScreenHandlerType
import net.minecraft.screen.slot.Slot
import net.minecraft.screen.slot.SlotActionType

class SpectatorInventoryScreenHandler(
    type: ScreenHandlerType<*>,
    syncId: Int,
    rows: Int,
    playerInventory:  PlayerInventory,
    inventory: Inventory,
): ScreenHandler(type, syncId) {

    init {
        // chest inventory slots
        for (row in 0 until rows) {
            for (col in 0 until 9) {
                val index = col + row * 9
                val x = 8 + col * 18
                val y = 18 + row * 18

                if (col in 2 until 7) {
                    this.addSlot(EmptySlot(inventory, index, x, y))
                } else {
                    this.addSlot(EmptySlot(inventory, index, x, y))
                }
            }
        }

        // player inventory -40 224
        val offset = rows*18 + 12
        for (row in 0 until 3) {
            for (col in 0 until 9) {
                this.addSlot(Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, offset + row * 18))
            }
        }

        // hotbar
        for (col in 0 until 9) {
            this.addSlot(Slot(playerInventory, col, 8 + col * 18, offset + 58))
        }
    }

    override fun insertItem(stack: ItemStack, startIndex: Int, endIndex: Int, fromLast: Boolean): Boolean {
        return false
    }

    override fun canUse(player: PlayerEntity): Boolean {
        return true
    }

    override fun onContentChanged(inventory: Inventory?) {
    }

    override fun quickMove(player: PlayerEntity?, slot: Int): ItemStack {
        return ItemStack.EMPTY
    }

    override fun onSlotClick(slotIndex: Int, button: Int, actionType: SlotActionType?, player: PlayerEntity?) {
    }
}