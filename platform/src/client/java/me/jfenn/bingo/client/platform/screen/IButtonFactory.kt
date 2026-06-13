package me.jfenn.bingo.client.platform.screen

import me.jfenn.bingo.platform.text.IText
import me.jfenn.bingo.platform.utils.IEventListener
import net.minecraft.client.gui.widget.ButtonWidget
import org.joml.Vector2i

interface IButtonFactory {

    fun createDefaultButton(
        message: IText,
        onClick: () -> Unit,
    ): IButton

    fun createButton(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        texture: String?,
        focusedTexture: String?,
        inactiveTexture: String? = texture,
        onClick: (ButtonWidget) -> Unit,
        message: IText,
    ) : IButton

    fun createNinePatchButton(
        sliceSize: Int,
        textureSize: Vector2i,
        texture: String?,
        focusedTexture: String?,
        inactiveTexture: String? = texture,
    ) : IButton

}

interface IButton {
    var position: Vector2i
    var size: Vector2i
    var message: IText
    var active: Boolean
    val onClick: IEventListener<Unit>
}