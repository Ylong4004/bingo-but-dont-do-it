package me.jfenn.bingo.common.scoring

import me.jfenn.bingo.platform.IPacketBuf
import me.jfenn.bingo.common.MOD_ID_BINGO
import me.jfenn.bingo.platform.packet.PacketConverter
import net.minecraft.util.Identifier

class ScoredItemLostPacket {
    object V1 : PacketConverter<ScoredItemLostPacket> {
        override val id = Identifier.of(MOD_ID_BINGO, "scored_item_lost")!!

        override fun fromPacketBuf(buf: IPacketBuf): ScoredItemLostPacket {
            return ScoredItemLostPacket()
        }

        override fun toPacketBuf(source: ScoredItemLostPacket, dest: IPacketBuf) {}
    }
}