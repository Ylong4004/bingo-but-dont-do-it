package me.jfenn.bingo.common.timer

import me.jfenn.bingo.platform.IPacketBuf
import me.jfenn.bingo.common.MOD_ID_BINGO
import me.jfenn.bingo.platform.packet.PacketConverter
import net.minecraft.util.Identifier

class CountdownPacket(
    val secondsRemaining: Int,
) {
    object V1 : PacketConverter<CountdownPacket> {
        override val id = Identifier.of(MOD_ID_BINGO, "countdown")!!

        override fun fromPacketBuf(buf: IPacketBuf): CountdownPacket {
            return CountdownPacket(buf.readInt())
        }

        override fun toPacketBuf(source: CountdownPacket, dest: IPacketBuf) {
            dest.writeInt(source.secondsRemaining)
        }
    }
}