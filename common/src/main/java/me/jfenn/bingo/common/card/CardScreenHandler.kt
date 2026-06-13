package me.jfenn.bingo.common.card

import me.jfenn.bingo.common.map.CardView
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.platform.item.IItemStackFactory
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.ScreenHandlerType
import net.minecraft.screen.slot.Slot
import net.minecraft.screen.slot.SlotActionType

class CardScreenHandler(
    syncId: Int,
    view: CardView,
    itemStackFactory: IItemStackFactory,
    playerInventory: PlayerInventory,
    text: TextProvider,
) : ScreenHandler(ScreenHandlerType.GENERIC_9X5, syncId) {

    init {
        val inventory = CardInventory(view, itemStackFactory, text)

        // bingo card slots
        for (row in 0 until 5) {
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

        // player inventory
        val offset = 5*18 + 12
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


class EmptySlot(inventory: Inventory, index: Int, x: Int, y: Int) : Slot(inventory, index, x, y) {
    override fun canInsert(stack: ItemStack): Boolean {
        return false
    }

    override fun canTakeItems(playerEntity: PlayerEntity): Boolean {
        return false
    }

    override fun isEnabled(): Boolean {
        return false
    }
}