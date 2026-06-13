package me.jfenn.bingo.client.integrations.jei

import net.minecraft.item.ItemStack

interface IJeiApi {
    fun openItemRecipe(stack: ItemStack): Boolean
}