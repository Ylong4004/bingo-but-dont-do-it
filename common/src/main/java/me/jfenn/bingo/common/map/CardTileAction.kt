package me.jfenn.bingo.common.map

import me.jfenn.bingo.common.MOD_ID_BINGO
import me.jfenn.bingo.platform.IPacketBuf
import me.jfenn.bingo.platform.item.IItemStack
import me.jfenn.bingo.platform.packet.PacketConverter
import net.minecraft.util.Identifier

sealed interface CardTileAction {
    data class Advancement(val id: String) : CardTileAction
    class Item(val item: IItemStack) : CardTileAction {
        override fun equals(other: Any?): Boolean {
            return other is Item && other.item.identifier == this.item.identifier
        }
        override fun hashCode(): Int {
            return item.identifier.hashCode()
        }
    }
    data object None : CardTileAction

    object V1 : PacketConverter<CardTileAction> {
        override val id: Identifier = Identifier.of(MOD_ID_BINGO, "card_tile_action")!!

        override fun fromPacketBuf(buf: IPacketBuf): CardTileAction {
            val type = buf.readString()
            return when (type) {
                "advancement" -> Advancement(buf.readString())
                "item" -> Item(buf.readItemStack())
                else -> None
            }
        }

        override fun toPacketBuf(source: CardTileAction, dest: IPacketBuf) {
            when (source) {
                is Advancement -> {
                    dest.writeString("advancement")
                    dest.writeString(source.id)
                }
                is Item -> {
                    dest.writeString("item")
                    dest.writeItemStack(source.item)
                }
                else -> dest.writeString("none")
            }
        }

    }
}