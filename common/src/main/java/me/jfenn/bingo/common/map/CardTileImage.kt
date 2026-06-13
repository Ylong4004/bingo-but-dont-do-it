package me.jfenn.bingo.common.map

import kotlinx.serialization.Serializable
import me.jfenn.bingo.common.MOD_ID_BINGO
import me.jfenn.bingo.platform.IPacketBuf
import me.jfenn.bingo.platform.item.IItemStackSerialized
import me.jfenn.bingo.platform.packet.PacketConverter
import net.minecraft.util.Identifier

@Serializable
class CardTileImage(
    val item: IItemStackSerialized? = null,
    val texture: String? = null,
    val mapTexture: String? = null,
) {

    companion object {
        val EMPTY = CardTileImage(null, null)
    }

    override fun hashCode(): Int {
        return arrayOf(item?.identifier, texture, mapTexture).contentHashCode()
    }

    override fun equals(other: Any?): Boolean {
        return other is CardTileImage &&
                item?.identifier == other.item?.identifier &&
                texture == other.texture &&
                mapTexture == other.mapTexture
    }

    object V1 : PacketConverter<CardTileImage> {
        override val id: Identifier = Identifier.of(MOD_ID_BINGO, "card_tile_image")!!

        override fun fromPacketBuf(buf: IPacketBuf): CardTileImage {
            return CardTileImage(
                item = buf.readNullable(buf::readItemStack),
                texture = buf.readNullable(buf::readString)
            )
        }

        override fun toPacketBuf(source: CardTileImage, dest: IPacketBuf) {
            dest.writeNullable(source.item, dest::writeItemStack)
            dest.writeNullable(source.texture, dest::writeString)
        }
    }
}