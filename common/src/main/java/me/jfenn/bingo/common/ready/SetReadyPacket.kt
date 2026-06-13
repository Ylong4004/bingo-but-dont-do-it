package me.jfenn.bingo.common.ready

import me.jfenn.bingo.platform.IPacketBuf
import me.jfenn.bingo.common.MOD_ID_BINGO
import me.jfenn.bingo.platform.packet.PacketConverter
import net.minecraft.util.Identifier

class SetReadyPacket(
    val isReady: Boolean
) {
    object V1 : PacketConverter<SetReadyPacket> {
        override val id: Identifier = Identifier.of(MOD_ID_BINGO, "set_ready")!!

        override fun toPacketBuf(source: SetReadyPacket, dest: IPacketBuf) {
            dest.writeBoolean(source.isReady)
        }

        override fun fromPacketBuf(buf: IPacketBuf): SetReadyPacket {
            return SetReadyPacket(buf.readBoolean())
        }
    }
}