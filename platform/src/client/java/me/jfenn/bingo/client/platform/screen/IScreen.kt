package me.jfenn.bingo.client.platform.screen

import me.jfenn.bingo.client.platform.renderer.IDrawService

interface IScreen {
    fun init() = Unit
    fun resize(width: Int, height: Int) = Unit
    fun beforeRender(drawService: IDrawService) = Unit
    fun render(drawService: IDrawService, mouseX: Int, mouseY: Int, delta: Float)
    fun mouseDragged(mouseX: Double, mouseY: Double): Boolean = false
    fun mouseReleased(mouseX: Double, mouseY: Double): Boolean = false
    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean = false
    fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean = false
    fun keyPressed(input: IKeyInput) = false
    fun shouldPause(): Boolean = false
    fun shouldCloseOnEsc(): Boolean = true
    fun onClose(): Unit = Unit
}

interface IKeyInput {
    val isEscape: Boolean
}
