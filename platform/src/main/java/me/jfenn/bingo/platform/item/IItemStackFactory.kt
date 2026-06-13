package me.jfenn.bingo.platform.item

import kotlinx.serialization.Contextual
import me.jfenn.bingo.platform.text.IText
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.server.MinecraftServer
import net.minecraft.util.Identifier

interface IItemStackFactory {
    val emptyStack: IItemStack

    fun listItems(server: MinecraftServer): List<String>
    fun isEnabledInWorld(item: String, server: MinecraftServer): Boolean

    fun createStack(item: String, count: Int = 1): IItemStack
    fun createStack(item: Identifier, count: Int = 1): IItemStack
    fun createStack(item: Item, count: Int = 1): IItemStack
    fun forStack(stack: ItemStack?): IItemStack

    fun createFilledMap(): IFilledMap
    fun createFireworkRocket(): IFireworkRocket
    fun createWrittenBook(): IWrittenBook
    fun createPlayerHead(): IPlayerHead
}

interface IItemStack {
    val stack: ItemStack
    val item: Item
    val identifier: Identifier
    var count: Int
    val maxCount: Int
    val isEmpty get() = count <= 0

    val displayName: IText
    val lore: List<IText>

    fun addCustomTag(tag: String)
    fun removeCustomTag(tag: String)
    fun hasCustomTag(tag: String): Boolean

    fun setDisplay(
        name: IText?,
        lore: List<IText>?,
    )

    fun setUnbreakable(value: Boolean)
    fun setHideFlags(hideFlags: Int)

    fun setNbtString(nbt: String?): Boolean
    fun getNbtString(): String?

    fun setComponentsString(components: Map<String, String?>): Boolean
    fun getComponentsString(): Map<String, String?>?

    fun isDataOverlapping(
        nbt: String?,
        components: Map<String, String?>?,
    ): Boolean

    fun copy(): IItemStack

    fun asFilledMap(): IFilledMap?
    fun asFireworkRocket(): IFireworkRocket?
    fun asWrittenBook(): IWrittenBook?
    fun asPlayerHead(): IPlayerHead?
}

typealias IItemStackSerialized = @Contextual IItemStack
