package me.jfenn.bingo.impl.inventory

import me.jfenn.bingo.platform.inventory.IContainerItemView
import me.jfenn.bingo.platform.item.IItemStack

interface ContainerItemView {
    class Inventory(
        override val stack: IItemStack
    ) : IContainerItemView {
        override fun replace(newStack: IItemStack) {}
    }
}