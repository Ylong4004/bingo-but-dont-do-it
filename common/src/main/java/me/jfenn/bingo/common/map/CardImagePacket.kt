package me.jfenn.bingo.common.map

import me.jfenn.bingo.platform.IPacketBuf
import me.jfenn.bingo.common.MOD_ID_BINGO
import me.jfenn.bingo.platform.packet.PacketConverter
import net.minecraft.util.Identifier

class CardImagePacket(
    val id: String,
    val image: ByteArray?,
) {
    object V1 : PacketConverter<CardImagePacket> {
        override val id: Identifier = Identifier.of(MOD_ID_BINGO, "card_image")!!

        override fun fromPacketBuf(buf: IPacketBuf): CardImagePacket {
            val id = buf.readString()
            val image = buf.readNullable(buf::readByteArray)
            return CardImagePacket(
                id = id,
                image = image,
            )
        }

        override fun toPacketBuf(source: CardImagePacket, dest: IPacketBuf) {
            dest.writeString(source.id)
            dest.writeNullable(source.image, dest::writeByteArray)
        }
    }
}