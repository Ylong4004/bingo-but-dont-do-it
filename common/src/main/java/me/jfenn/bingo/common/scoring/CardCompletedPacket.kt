package me.jfenn.bingo.common.scoring

import me.jfenn.bingo.platform.IPacketBuf
import me.jfenn.bingo.common.MOD_ID_BINGO
import me.jfenn.bingo.platform.packet.PacketConverter
import net.minecraft.util.Identifier

class CardCompletedPacket(
    val isWinner: Boolean,
) {
    object V1 : PacketConverter<CardCompletedPacket> {
        override val id: Identifier = Identifier.of(MOD_ID_BINGO, "card_completed")!!

        override fun fromPacketBuf(buf: IPacketBuf): CardCompletedPacket {
            return CardCompletedPacket(
                isWinner = buf.readBoolean()
            )
        }

        override fun toPacketBuf(source: CardCompletedPacket, dest: IPacketBuf) {
            dest.writeBoolean(source.isWinner)
        }
    }
}

