package me.jfenn.bingo.common.menu

import me.jfenn.bingo.platform.text.IText
import me.jfenn.bingo.common.utils.plus
import me.jfenn.bingo.platform.EntityType
import me.jfenn.bingo.platform.IDisplayEntity
import me.jfenn.bingo.platform.ITextDisplayEntity
import net.minecraft.text.Text
import org.joml.Matrix4f
import org.joml.Vector3d

internal fun MenuComponent.registerTitlePanel(
    position: Vector3d,
    width: Double,
    height: Double = MENU_LINE_HEIGHT,
    title: IText = this.text.empty(),
    titleProp: Property<IText> = ConstantProperty(title),
) {
    val titleText by titleProp

    registerEntity(EntityType.TEXT_DISPLAY) {
        pos = position + Vector3d(0.0, 0.05, 0.0)
        this.value = titleText.apply { setColor(0x99ff99) }
        billboard = ITextDisplayEntity.Billboard.FIXED
        alignment = ITextDisplayEntity.TextAlignment.CENTER
        background = 0
        transformation = Matrix4f().scale(MENU_TEXT_SCALE)
    }

    registerEntity(EntityType.BLOCK_DISPLAY) {
        pos = position + Vector3d(-width/2, 0.0, -0.051)
        blockIdentifier = if (titleText != Text.empty()) MENU_TITLE_MATERIAL else "minecraft:air"
        brightness = IDisplayEntity.Brightness(0, 3)
        transformation = Matrix4f().scale(width.toFloat(), height.toFloat(), 0.05f)
    }
}