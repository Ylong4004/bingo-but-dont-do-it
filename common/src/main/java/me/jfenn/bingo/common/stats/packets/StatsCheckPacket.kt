package me.jfenn.bingo.common.stats.packets

import me.jfenn.bingo.platform.IPacketBuf
import me.jfenn.bingo.common.MOD_ID_BINGO
import me.jfenn.bingo.platform.packet.PacketConverter
import net.minecraft.util.Identifier

class StatsCheckPacket(
    val hashSha512: String,
) {
    object V1 : PacketConverter<StatsCheckPacket> {
        override val id = Identifier.of(MOD_ID_BINGO, "stats_check")!!

        override fun toPacketBuf(source: StatsCheckPacket, dest: IPacketBuf) {
            dest.writeString(source.hashSha512)
        }

        override fun fromPacketBuf(buf: IPacketBuf): StatsCheckPacket {
            return StatsCheckPacket(
                hashSha512 = buf.readString()
            )
        }
    }
}