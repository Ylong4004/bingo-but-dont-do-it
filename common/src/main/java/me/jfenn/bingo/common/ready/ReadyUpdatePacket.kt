package me.jfenn.bingo.common.ready

import me.jfenn.bingo.platform.text.IText
import me.jfenn.bingo.common.MOD_ID_BINGO
import me.jfenn.bingo.common.state.GameState
import me.jfenn.bingo.platform.IPacketBuf
import me.jfenn.bingo.platform.packet.PacketConverter
import net.minecraft.util.Identifier
import java.time.Duration

data class ReadyUpdatePacket(
    val isRunning: Boolean,
    val isReady: Boolean,
    val state: GameState,
    val remainingDuration: Duration,
    val totalDuration: Duration,
    val readyPlayers: Int,
    val totalPlayers: Int,
    val title: IText?,
    val subtitle: IText?,
    val canSendReady: Boolean = false,
) {
    object V1 : PacketConverter<ReadyUpdatePacket> {
        override val id: Identifier = Identifier.of(MOD_ID_BINGO, "ready_timer_update")!!

        override fun fromPacketBuf(buf: IPacketBuf): ReadyUpdatePacket {
            return ReadyUpdatePacket(
                isRunning = buf.readBoolean(),
                isReady = buf.readBoolean(),
                state = try {
                    GameState.valueOf(buf.readString())
                } catch (e: IllegalArgumentException) {
                    GameState.PREGAME
                },
                remainingDuration = buf.readDuration(),
                totalDuration = buf.readDuration(),
                readyPlayers = buf.readInt(),
                totalPlayers = buf.readInt(),
                title = null,
                subtitle = null,
            )
        }

        override fun toPacketBuf(source: ReadyUpdatePacket, dest: IPacketBuf) {
            dest.writeBoolean(source.isRunning)
            dest.writeBoolean(source.isReady)
            dest.writeString(source.state.name)
            dest.writeDuration(source.remainingDuration)
            dest.writeDuration(source.totalDuration)
            dest.writeInt(source.readyPlayers)
            dest.writeInt(source.totalPlayers)
        }
    }

    object V2 : PacketConverter<ReadyUpdatePacket> {
        override val id: Identifier = Identifier.of(MOD_ID_BINGO, "ready_timer_update_v2")!!

        override fun fromPacketBuf(buf: IPacketBuf): ReadyUpdatePacket {
            return ReadyUpdatePacket(
                isRunning = buf.readBoolean(),
                isReady = buf.readBoolean(),
                state = try {
                    GameState.valueOf(buf.readString())
                } catch (e: IllegalArgumentException) {
                    GameState.PREGAME
                },
                remainingDuration = buf.readDuration(),
                totalDuration = buf.readDuration(),
                readyPlayers = buf.readInt(),
                totalPlayers = buf.readInt(),
                title = buf.readNullable(buf::readText),
                subtitle = buf.readNullable(buf::readText),
            )
        }

        override fun toPacketBuf(source: ReadyUpdatePacket, dest: IPacketBuf) {
            dest.writeBoolean(source.isRunning)
            dest.writeBoolean(source.isReady)
            dest.writeString(source.state.name)
            dest.writeDuration(source.remainingDuration)
            dest.writeDuration(source.totalDuration)
            dest.writeInt(source.readyPlayers)
            dest.writeInt(source.totalPlayers)
            dest.writeNullable(source.title, dest::writeText)
            dest.writeNullable(source.subtitle, dest::writeText)
        }
    }

    object V3 : PacketConverter<ReadyUpdatePacket> {
        override val id: Identifier = Identifier.of(MOD_ID_BINGO, "ready_timer_update_v3")!!

        override fun fromPacketBuf(buf: IPacketBuf): ReadyUpdatePacket {
            return ReadyUpdatePacket(
                isRunning = buf.readBoolean(),
                isReady = buf.readBoolean(),
                state = try {
                    GameState.valueOf(buf.readString())
                } catch (e: IllegalArgumentException) {
                    GameState.PREGAME
                },
                remainingDuration = buf.readDuration(),
                totalDuration = buf.readDuration(),
                readyPlayers = buf.readInt(),
                totalPlayers = buf.readInt(),
                title = buf.readNullable(buf::readText),
                subtitle = buf.readNullable(buf::readText),
                canSendReady = buf.readBoolean(),
            )
        }

        override fun toPacketBuf(source: ReadyUpdatePacket, dest: IPacketBuf) {
            dest.writeBoolean(source.isRunning)
            dest.writeBoolean(source.isReady)
            dest.writeString(source.state.name)
            dest.writeDuration(source.remainingDuration)
            dest.writeDuration(source.totalDuration)
            dest.writeInt(source.readyPlayers)
            dest.writeInt(source.totalPlayers)
            dest.writeNullable(source.title, dest::writeText)
            dest.writeNullable(source.subtitle, dest::writeText)
            dest.writeBoolean(source.canSendReady)
        }
    }
}