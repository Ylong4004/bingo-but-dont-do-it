package me.jfenn.bingo.common.timer

import me.jfenn.bingo.platform.IPacketBuf
import me.jfenn.bingo.common.MOD_ID_BINGO
import me.jfenn.bingo.platform.packet.PacketConverter
import net.minecraft.util.Identifier

class TimerPacket(
    val secondsRemaining: Int,
) {
    object V1 : PacketConverter<TimerPacket> {
        override val id = Identifier.of(MOD_ID_BINGO, "timer")!!

        override fun fromPacketBuf(buf: IPacketBuf): TimerPacket {
            return TimerPacket(buf.readInt())
        }

        override fun toPacketBuf(source: TimerPacket, dest: IPacketBuf) {
            dest.writeInt(source.secondsRemaining)
        }
    }
}