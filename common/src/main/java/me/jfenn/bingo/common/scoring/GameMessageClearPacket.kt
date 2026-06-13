package me.jfenn.bingo.common.scoring

import me.jfenn.bingo.common.MOD_ID_BINGO
import me.jfenn.bingo.platform.IPacketBuf
import me.jfenn.bingo.platform.packet.PacketConverter
import net.minecraft.util.Identifier

object GameMessageClearPacket {
    object V1 : PacketConverter<GameMessageClearPacket> {
        override val id: Identifier = Identifier.of(MOD_ID_BINGO, "game_message_clear")!!

        override fun toPacketBuf(source: GameMessageClearPacket, dest: IPacketBuf) {
            // no-op
        }

        override fun fromPacketBuf(buf: IPacketBuf): GameMessageClearPacket {
            return GameMessageClearPacket
        }
    }
}