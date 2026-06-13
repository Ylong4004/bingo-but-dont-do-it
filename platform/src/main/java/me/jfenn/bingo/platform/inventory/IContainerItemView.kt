package me.jfenn.bingo.platform.inventory

import me.jfenn.bingo.platform.item.IItemStack

interface IContainerItemView {
    val stack: IItemStack
    fun mutate(closure: (IItemStack) -> Unit) {
        closure(stack)
        replace(stack)
    }
    fun replace(newStack: IItemStack)
}