package me.jfenn.bingo.platform

interface IRecipeManager {
    fun listRecipes(): List<IRecipe>
    fun lockRecipes(player: IPlayerHandle, recipes: List<IRecipe>)
    fun unlockRecipes(player: IPlayerHandle, recipes: List<IRecipe>)
}

interface IRecipe
