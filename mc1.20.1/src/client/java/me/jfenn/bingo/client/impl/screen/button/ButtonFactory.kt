package me.jfenn.bingo.client.impl.screen.button

import me.jfenn.bingo.client.impl.draw.DrawService
import me.jfenn.bingo.client.platform.screen.IButton
import me.jfenn.bingo.client.platform.screen.IButtonFactory
import me.jfenn.bingo.common.utils.EventListener
import me.jfenn.bingo.impl.TextImpl
import me.jfenn.bingo.platform.text.IText
import me.jfenn.bingo.platform.utils.IEventListener
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.ClickableWidget
import net.minecraft.client.gui.widget.TexturedButtonWidget
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import org.joml.Vector2i

class ButtonFactory : IButtonFactory {
    override fun createDefaultButton(
        message: IText,
        onClick: () -> Unit
    ) = ButtonWidget.builder(message.value, { onClick() })
        .build()
        .let { ButtonWidgetImpl(it) }

    override fun createButton(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        texture: String?,
        focusedTexture: String?,
        inactiveTexture: String?,
        onClick: (ButtonWidget) -> Unit,
        message: IText
    ): IButton = LegacyTexturedButton(
        x,
        y,
        width,
        height,
        texture?.let { Identifier(it) },
        focusedTexture?.let { Identifier(it) },
        inactiveTexture?.let { Identifier(it) },
        onClick,
        message.value,
    ).let { TexturedButtonImpl(it) }

    override fun createNinePatchButton(
        sliceSize: Int,
        textureSize: Vector2i,
        texture: String?,
        focusedTexture: String?,
        inactiveTexture: String?,
    ): IButton {
        return NinePatchButton(sliceSize, textureSize, texture, focusedTexture, inactiveTexture)
    }
}

interface ButtonImpl : IButton {
    val button: ClickableWidget
}

class LegacyTexturedButton(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    private val normalTexture: Identifier?,
    private val focusedTexture: Identifier?,
    private val inactiveTexture: Identifier?,
    onClick: (ButtonWidget) -> Unit,
    message: Text,
) : TexturedButtonWidget(
    x,
    y,
    width,
    height,
    0,
    0,
    0,
    null,
    width,
    height,
    onClick,
    message,
) {
    fun setHeight(height: Int) {
        this.height = height
    }

    override fun renderButton(context: DrawContext?, mouseX: Int, mouseY: Int, delta: Float) {
        val id = when {
            !active -> inactiveTexture
            isSelected -> focusedTexture
            else -> normalTexture
        }
        context?.drawTexture(
            id?.let { Identifier.of(id.namespace, "textures/gui/sprites/${id.path}.png") },
            x,
            y,
            u.toFloat(),
            v.toFloat(),
            width,
            height,
            textureWidth,
            textureHeight,
        )
    }
}

class TexturedButtonImpl(
    override val button: LegacyTexturedButton
) : ButtonImpl {
    override var position: Vector2i
        get() = Vector2i(button.x, button.y)
        set(value) {
            button.x = value.x
            button.y = value.y
        }

    override var size: Vector2i
        get() = Vector2i(button.width, button.height)
        set(value) {
            button.width = value.x
            button.height = value.y
        }

    override var message: IText
        get() = TextImpl(button.message.copy())
        set(value) {
            button.message = value.value
        }

    override var active: Boolean by button::active

    override val onClick: IEventListener<Unit> = EventListener()
}

class ButtonWidgetImpl(
    override val button: ButtonWidget
) : ButtonImpl {
    override var position: Vector2i
        get() = Vector2i(button.x, button.y)
        set(value) {
            button.x = value.x
            button.y = value.y
        }

    override var size: Vector2i
        get() = Vector2i(button.width, button.height)
        set(value) {
            button.width = value.x
        }

    override var message: IText
        get() = TextImpl(button.message.copy())
        set(value) {
            button.message = value.value
        }

    override var active: Boolean by button::active

    override val onClick: IEventListener<Unit> = EventListener()
}

class NinePatchButton(
    private val sliceSize: Int,
    private val textureSize: Vector2i,
    texture: String?,
    focusedTexture: String?,
    inactiveTexture: String?,
) : ButtonImpl {

    val textureId = texture?.let { Identifier(it) }
    val focusedTextureId = focusedTexture?.let { Identifier(it) }
    val inactiveTextureId = inactiveTexture?.let { Identifier(it) }

    private interface ButtonWidgetExt {
        fun bingoSetHeight(height: Int)
    }

    override val button: ButtonWidget = object : ButtonWidget(
        0, 0, 0, 0,
        Text.empty(),
        { onClick.invoke(Unit) },
        DEFAULT_NARRATION_SUPPLIER
    ), ButtonWidgetExt {
        override fun bingoSetHeight(height: Int) {
            this.height = height
        }

        override fun renderButton(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
            val service = DrawService(context)
            val id = when {
                !active -> inactiveTextureId
                isSelected -> focusedTextureId
                else -> textureId
            } ?: return
            service.drawNinePatch(id, x, y, width, height, sliceSize, textureSize.x, textureSize.y)
            drawMessage(context, MinecraftClient.getInstance().textRenderer, -1)
        }
    }

    override var position: Vector2i
        get() = Vector2i(button.x, button.y)
        set(value) {
            button.x = value.x
            button.y = value.y
        }

    override var size: Vector2i
        get() = Vector2i(button.width, button.height)
        set(value) {
            button.width = value.x
            (button as ButtonWidgetExt).bingoSetHeight(value.y)
        }

    override var message: IText
        get() = TextImpl(button.message.copy())
        set(value) {
            button.message = value.value
        }

    override var active: Boolean by button::active

    override val onClick: IEventListener<Unit> = EventListener()
}
