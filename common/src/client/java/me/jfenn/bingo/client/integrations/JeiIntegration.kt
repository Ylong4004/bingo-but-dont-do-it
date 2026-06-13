package me.jfenn.bingo.client.integrations

import me.jfenn.bingo.client.integrations.jei.IJeiApi
import net.minecraft.item.ItemStack

internal class JeiIntegration(
    private val integrations: List<IJeiApi>,
) : IJeiApi {

    override fun openItemRecipe(stack: ItemStack): Boolean {
        return integrations.find { it.openItemRecipe(stack) } != null
    }
}