package me.jfenn.bingo.client.impl

import me.jfenn.bingo.client.impl.draw.DrawService
import me.jfenn.bingo.client.platform.IScrollableWidget
import me.jfenn.bingo.client.platform.IScrollableWidgetFactory
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder
import net.minecraft.text.Text

class ScrollableWidget(
    private val content: IScrollableWidget,
) : net.minecraft.client.gui.widget.ScrollableTextFieldWidget(0, 0, 0, 0, Text.empty()) {
    // override the hardcoded padding values
    private var contentPadding = 4

    override fun getPadding(): Int {
        return contentPadding
    }

    override fun getContentsHeight(): Int {
        return content.measureContentHeight()
    }

    // don't draw the box background
    override fun drawBox(context: DrawContext?) {}

    override fun appendClickableNarrations(builder: NarrationMessageBuilder?) {}

    override fun getDeltaYPerScroll(): Double {
        return 9.0
    }

    override fun renderWidget(context: DrawContext?, mouseX: Int, mouseY: Int, delta: Float) {
        this.x = content.x
        this.y = content.y
        this.width = content.width
        this.height = content.height
        this.contentPadding = content.padding

        super.renderWidget(context, mouseX, mouseY, delta)
    }

    override fun renderContents(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        val drawService = DrawService(context)
        drawService.delta = delta

        if (content.background != 0) {
            context.fill(x, y, x + width, y + contentsHeight + contentPadding*2, content.background)
        }

        context.matrices.pushMatrix()
        context.matrices.translate((x + contentPadding).toFloat(), (y + contentPadding).toFloat())
        content.renderContents(drawService, mouseX - (x + contentPadding), mouseY - (y + contentPadding))
        context.matrices.popMatrix()
    }
}

object ScrollableWidgetFactory : IScrollableWidgetFactory {
    override fun create(widget: IScrollableWidget): IScrollableWidget {
        widget.widget = ScrollableWidget(widget)
        return widget
    }
}
