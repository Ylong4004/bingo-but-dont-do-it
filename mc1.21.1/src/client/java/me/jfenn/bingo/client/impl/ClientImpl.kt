package me.jfenn.bingo.client.impl

import me.jfenn.bingo.client.impl.draw.FontImpl
import me.jfenn.bingo.client.platform.IClient
import me.jfenn.bingo.client.platform.IClientPlayer
import me.jfenn.bingo.client.platform.renderer.IFont
import me.jfenn.bingo.platform.text.IText
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.Screen
import org.joml.Vector2i
import java.util.concurrent.Executor

class ClientImpl : IClient {
    private val client = MinecraftClient.getInstance()

    override val font: IFont
        get() = FontImpl(client.textRenderer)

    override val player: IClientPlayer = PlayerImpl()

    override val isInSingleplayer: Boolean
        get() = client.isInSingleplayer

    override val isPaused: Boolean
        get() = client.isPaused

    override val mouse: Vector2i
        get() {
            val mouse = client.mouse
            return Vector2i(
                (mouse.x * client.window.scaledWidth.toDouble() / client.window.width.toDouble()).toInt(),
                (mouse.y * client.window.scaledHeight.toDouble() / client.window.height.toDouble()).toInt(),
            )
        }

    override var screen: Screen?
        get() = client.currentScreen
        set(value) { client.setScreen(value) }

    override fun execute(callback: () -> Unit) {
        client.execute(callback)
    }

    override val executor: Executor = client

    inner class PlayerImpl : IClientPlayer {
        override fun sendHotbarMessage(text: IText) {
            client.player?.sendMessage(text.value, true)
        }
        override fun sendCommand(command: String) {
            client.player?.networkHandler?.sendCommand(
                command.removePrefix("/")
            )
        }
    }
}