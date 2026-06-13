package me.jfenn.bingo.integrations.jei

import dev.emi.emi.api.EmiApi
import dev.emi.emi.api.stack.EmiStack
import me.jfenn.bingo.client.integrations.jei.IJeiApi
import net.minecraft.item.ItemStack

class EmiApi : IJeiApi {
    override fun openItemRecipe(stack: ItemStack): Boolean {
        EmiApi.displayRecipes(EmiStack.of(stack))
        return true
    }
}