package me.jfenn.bingo.client.platform.screen

import me.jfenn.bingo.client.platform.renderer.IDrawService
import net.minecraft.client.gui.Drawable
import net.minecraft.client.gui.screen.Screen
import org.joml.Vector2i

interface IScreenHelper {
    val screen: Screen
    val width: Int
    val height: Int
    fun onAfterLeftClick(callback: (Vector2i) -> Unit)
    fun addButton(button: IButton)
    fun close()
}

interface IMutableScreenHelper : IScreenHelper {
    fun addDrawableChild(drawable: Drawable)
    fun addDrawable(drawable: IDrawable)
    fun clearChildren()
}

interface IDrawable {
    fun render(drawService: IDrawService)
}
