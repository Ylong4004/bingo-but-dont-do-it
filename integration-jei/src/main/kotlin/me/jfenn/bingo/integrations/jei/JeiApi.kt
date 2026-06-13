package me.jfenn.bingo.integrations.jei

import me.jfenn.bingo.client.integrations.jei.IJeiApi
import mezz.jei.api.constants.VanillaTypes
import mezz.jei.api.recipe.RecipeIngredientRole
import net.minecraft.item.ItemStack
import org.slf4j.LoggerFactory

class JeiApi : IJeiApi {
    private val log = LoggerFactory.getLogger("bingo")

    override fun openItemRecipe(stack: ItemStack): Boolean {
        val runtime = JeiEntrypoint.runtime ?: run {
            log.error("JEI Runtime not available!")
            return false
        }

        val focus = runtime.jeiHelpers.focusFactory.createFocus(
            RecipeIngredientRole.OUTPUT,
            VanillaTypes.ITEM_STACK,
            stack,
        )

        runtime.recipesGui.show(focus)
        return true
    }
}