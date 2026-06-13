package me.jfenn.bingo.client.platform

import me.jfenn.bingo.client.platform.renderer.IFont
import net.minecraft.client.gui.screen.Screen
import org.joml.Vector2i
import java.util.concurrent.Executor

interface IClient {
    val font: IFont
    val player: IClientPlayer
    val isInSingleplayer: Boolean
    val isPaused: Boolean
    val mouse: Vector2i
    var screen: Screen?
    fun execute(callback: () -> Unit)
    val executor: Executor
}
