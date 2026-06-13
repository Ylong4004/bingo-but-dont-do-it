package me.jfenn.bingo.integrations.jei

import me.jfenn.bingo.client.integrations.jei.IJeiApi
import me.shedaniel.rei.api.client.view.ViewSearchBuilder
import me.shedaniel.rei.api.common.util.EntryStacks
import net.minecraft.item.ItemStack

class ReiApi : IJeiApi {
    override fun openItemRecipe(stack: ItemStack): Boolean {
        return ViewSearchBuilder.builder()
            .addRecipesFor(EntryStacks.of(stack))
            .open()
    }
}