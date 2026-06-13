package me.jfenn.bingo.impl

import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.IRecipe
import me.jfenn.bingo.platform.IRecipeManager
import net.minecraft.recipe.Recipe
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity

class RecipeManagerImpl(
    private val server: MinecraftServer,
) : IRecipeManager {
    override fun listRecipes(): List<IRecipe> {
        return server.recipeManager.values()
            .map { RecipeImpl(it) }
    }

    override fun lockRecipes(player: IPlayerHandle, recipes: List<IRecipe>) {
        val recipeEntries = recipes
            .filterIsInstance<RecipeImpl>()
            .map { it.recipe }

        val playerEntity: ServerPlayerEntity = player.player
        playerEntity.lockRecipes(recipeEntries)
    }

    override fun unlockRecipes(player: IPlayerHandle, recipes: List<IRecipe>) {
        val recipeEntries = recipes
            .filterIsInstance<RecipeImpl>()
            .map { it.recipe }

        val playerEntity: ServerPlayerEntity = player.player
        playerEntity.unlockRecipes(recipeEntries)
    }
}

class RecipeImpl(val recipe: Recipe<*>) : IRecipe
