package me.jfenn.bingo.common.menu

import me.jfenn.bingo.platform.item.IItemStack
import me.jfenn.bingo.common.NBT_BINGO_IGNORE
import me.jfenn.bingo.common.NBT_BINGO_KEEP
import me.jfenn.bingo.common.NBT_BINGO_VANISH
import me.jfenn.bingo.common.utils.EventListener
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.inventory.SimpleInventory
import net.minecraft.item.ItemStack
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.ScreenHandlerType
import net.minecraft.screen.slot.Slot
import org.koin.core.component.KoinComponent

internal class InventoryScreenHandler(
    syncId: Int,
    stacks: List<IItemStack>,
    playerInventory: PlayerInventory,
) : ScreenHandler(ScreenHandlerType.GENERIC_9X3, syncId), KoinComponent {

    val inventory = SimpleInventory(9*3)

    val onClose = EventListener<List<ItemStack>>()

    init {
        stacks.forEachIndexed { i, stack ->
            // remove bingo flags when in a config inventory
            stack.removeCustomTag(NBT_BINGO_IGNORE)
            stack.removeCustomTag(NBT_BINGO_VANISH)
            stack.removeCustomTag(NBT_BINGO_KEEP)
            inventory.setStack(i, stack.stack)
        }

        // inventory slots
        for (row in 0 until 3) {
            for (col in 0 until 9) {
                val index = col + row * 9
                val x = 8 + col * 18
                val y = 18 + row * 18

                if (col in 2 until 7) {
                    this.addSlot(Slot(inventory, index, x, y))
                } else {
                    this.addSlot(Slot(inventory, index, x, y))
                }
            }
        }

        // player inventory
        val offset = 3*18 + 12
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

    override fun canUse(player: PlayerEntity): Boolean {
        return true
    }

    /**
     * Refer to GenericContainerScreenHandler for how this should be implemented
     */
    override fun quickMove(player: PlayerEntity?, slot: Int): ItemStack {
        val slotInstance = slots[slot]
        if (!slotInstance.hasStack())
            return ItemStack.EMPTY

        val originalStack = slotInstance.stack
        val newStack = originalStack.copy()

        if (slot < 3*9) {
            if (!insertItem(originalStack, 3*9, slots.size, true))
                return ItemStack.EMPTY
        } else {
            if (!insertItem(originalStack, 0, 3*9, false))
                return ItemStack.EMPTY
        }

        slotInstance.markDirty()
        return newStack
    }

    override fun onClosed(player: PlayerEntity?) {
        super.onClosed(player)
        onClose(
            (0 until inventory.size())
                .map { inventory.getStack(it) }
                .filter { !it.isEmpty }
        )
    }
}
