package me.jfenn.bingo.client.impl.screen

import me.jfenn.bingo.client.impl.draw.DrawService
import me.jfenn.bingo.client.platform.screen.IScreen
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.*
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.input.KeyInput
import net.minecraft.text.Text
import kotlin.math.absoluteValue

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
        val service = DrawService(context)
        impl?.beforeRender(service)
        super.render(context, mouseX, mouseY, delta)
        impl?.render(service, mouseX, mouseY, delta)
    }

    override fun mouseDragged(click: Click, offsetX: Double, offsetY: Double): Boolean {
        return impl?.mouseDragged(click.x, click.y) == true || super.mouseDragged(click, offsetX, offsetY)
    }

    override fun mouseReleased(click: Click): Boolean {
        return impl?.mouseReleased(click.x, click.y) == true || super.mouseReleased(click)
    }

    override fun mouseClicked(click: Click, doubled: Boolean): Boolean {
        return impl?.mouseClicked(click.x, click.y, click.button()) == true || super.mouseClicked(click, doubled)
    }

    override fun keyPressed(input: KeyInput): Boolean {
        return impl?.keyPressed(KeyInputImpl(input)) == true || super.keyPressed(input)
    }

    override fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        horizontalAmount: Double,
        verticalAmount: Double
    ): Boolean {
        val amount = arrayOf(horizontalAmount, verticalAmount).maxBy { it.absoluteValue }
        return impl?.mouseScrolled(mouseX, mouseY, amount) == true || super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
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