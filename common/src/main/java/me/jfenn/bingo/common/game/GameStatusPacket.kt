package me.jfenn.bingo.common.game

import me.jfenn.bingo.common.MOD_ID_BINGO
import me.jfenn.bingo.platform.IPacketBuf
import me.jfenn.bingo.platform.packet.PacketConverter
import net.minecraft.util.Identifier
import java.time.Duration

class GameStatusPacket(
    val ingameDuration: Duration?,
    val remainingDuration: Duration?,
) {
    val isDefaultInstance get() = this === DEFAULT

    companion object {
        val DEFAULT = GameStatusPacket(
            ingameDuration = null,
            remainingDuration = null,
        )
    }

    object V1 : PacketConverter<GameStatusPacket> {
        override val id: Identifier = Identifier.of(MOD_ID_BINGO, "game_status")!!

        override fun fromPacketBuf(buf: IPacketBuf): GameStatusPacket {
            val duration = buf.readNullable(buf::readDuration)
            val timeRemaining = buf.readNullable(buf::readDuration)
            return GameStatusPacket(
                ingameDuration = duration,
                remainingDuration = timeRemaining,
            )
        }

        override fun toPacketBuf(source: GameStatusPacket, dest: IPacketBuf) {
            dest.writeNullable(source.ingameDuration, dest::writeDuration)
            dest.writeNullable(source.remainingDuration, dest::writeDuration)
        }
    }
}