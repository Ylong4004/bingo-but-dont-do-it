package me.jfenn.bingo.client.impl.screen

import me.jfenn.bingo.client.impl.draw.DrawService
import me.jfenn.bingo.client.platform.screen.IScreen
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.Drawable
import net.minecraft.client.gui.Element
import net.minecraft.client.gui.Selectable
import net.minecraft.client.gui.screen.Screen
import net.minecraft.text.Text

class ScreenImpl(title: Text) : Screen(title) {
    var impl: IScreen? = null
    val helper = MutableScreenHelperImpl(this)

    override fun init() {
        super.init()
        impl?.init()
    }

    fun bingoAddDrawableChild(drawable: Drawable) {
        require(drawable is Element) { "${drawable::class.simpleName} is not Element!" }
        require(drawable is Selectable) { "${drawable::class.simpleName} is not Element!" }
        addDrawableChild(drawable)
    }

    fun bingoClearChildren() {
        clearChildren()
    }

    override fun resize(client: MinecraftClient?, width: Int, height: Int) {
        super.resize(client, width, height)
        impl?.resize(width, height)
    }

    override fun render(context: DrawContext?, mouseX: Int, mouseY: Int, delta: Float) {
        if (context == null) return
        renderBackground(context)
        val service = DrawService(context)
        impl?.beforeRender(service)
        super.render(context, mouseX, mouseY, delta)
        impl?.render(service, mouseX, mouseY, delta)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean {
        return impl?.mouseDragged(mouseX, mouseY) == true || super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        return impl?.mouseReleased(mouseX, mouseY) == true || super.mouseReleased(mouseX, mouseY, button)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        return impl?.mouseClicked(mouseX, mouseY, button) == true || super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        return impl?.mouseScrolled(mouseX, mouseY, amount) == true || super.mouseScrolled(mouseX, mouseY, amount)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        return impl?.keyPressed(KeyInputImpl(keyCode)) == true || super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun shouldPause(): Boolean {
        return impl?.shouldPause() ?: false
    }

    override fun shouldCloseOnEsc(): Boolean {
        return impl?.shouldCloseOnEsc() ?: true
    }

    override fun close() {
        impl?.onClose()
        super.close()
    }
}