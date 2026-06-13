package me.jfenn.bingo.integrations.ddi

import me.jfenn.bingo.common.MOD_ID_BINGO
import me.jfenn.bingo.platform.IPacketBuf
import me.jfenn.bingo.platform.packet.PacketConverter
import net.minecraft.util.Identifier
import java.util.*

/**
 * DDI 网络包定义。使用 Bingo 的 PacketConverter 模式。
 */

/** S2C: 同步单个玩家的 DDI 词条和心数。 */
class DDIWordSyncPacket(
    val playerId: UUID,
    val playerName: String,
    val wordText: String,
    val hearts: Int,
    val maxHearts: Int,
    val timerSeconds: Int,
    val maxTimerSeconds: Int,
    val isEliminated: Boolean,
    val isSelf: Boolean,
) {
    object V1 : PacketConverter<DDIWordSyncPacket> {
        override val id: Identifier = Identifier.of(MOD_ID_BINGO, "ddi_word_sync")!!

        override fun fromPacketBuf(buf: IPacketBuf): DDIWordSyncPacket {
            return DDIWordSyncPacket(
                playerId = UUID(buf.readLong(), buf.readLong()),
                playerName = buf.readString(),
                wordText = buf.readString(),
                hearts = buf.readInt(),
                maxHearts = buf.readInt(),
                timerSeconds = buf.readInt(),
                maxTimerSeconds = buf.readInt(),
                isEliminated = buf.readBoolean(),
                isSelf = buf.readBoolean(),
            )
        }

        override fun toPacketBuf(source: DDIWordSyncPacket, dest: IPacketBuf) {
            dest.writeLong(source.playerId.mostSignificantBits)
            dest.writeLong(source.playerId.leastSignificantBits)
            dest.writeString(source.playerName)
            dest.writeString(source.wordText)
            dest.writeInt(source.hearts)
            dest.writeInt(source.maxHearts)
            dest.writeInt(source.timerSeconds)
            dest.writeInt(source.maxTimerSeconds)
            dest.writeBoolean(source.isEliminated)
            dest.writeBoolean(source.isSelf)
        }
    }
}

/** S2C: 通知有人触发了词条。 */
class DDITriggeredPacket(
    val playerId: UUID,
    val playerName: String,
    val wordText: String,
    val heartsRemaining: Int,
    val isElimination: Boolean,
    val isGain: Boolean,
) {
    object V1 : PacketConverter<DDITriggeredPacket> {
        override val id: Identifier = Identifier.of(MOD_ID_BINGO, "ddi_triggered")!!

        override fun fromPacketBuf(buf: IPacketBuf): DDITriggeredPacket {
            return DDITriggeredPacket(
                playerId = UUID(buf.readLong(), buf.readLong()),
                playerName = buf.readString(),
                wordText = buf.readString(),
                heartsRemaining = buf.readInt(),
                isElimination = buf.readBoolean(),
                isGain = buf.readBoolean(),
            )
        }

        override fun toPacketBuf(source: DDITriggeredPacket, dest: IPacketBuf) {
            dest.writeLong(source.playerId.mostSignificantBits)
            dest.writeLong(source.playerId.leastSignificantBits)
            dest.writeString(source.playerName)
            dest.writeString(source.wordText)
            dest.writeInt(source.heartsRemaining)
            dest.writeBoolean(source.isElimination)
            dest.writeBoolean(source.isGain)
        }
    }
}

/** S2C: 重置 DDI HUD 状态（游戏结束时）。 */
class DDIStateResetPacket {
    object V1 : PacketConverter<DDIStateResetPacket> {
        override val id: Identifier = Identifier.of(MOD_ID_BINGO, "ddi_state_reset")!!

        override fun fromPacketBuf(buf: IPacketBuf): DDIStateResetPacket = DDIStateResetPacket()
        override fun toPacketBuf(source: DDIStateResetPacket, dest: IPacketBuf) {}
    }
}
