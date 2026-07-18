package me.jfenn.bingo.integrations.ddi

import me.jfenn.bingo.common.MOD_ID_BINGO
import me.jfenn.bingo.platform.IPacketBuf
import me.jfenn.bingo.platform.packet.PacketConverter
import me.jfenn.bingo.platform.packet.IServerNetworking
import net.minecraft.util.Identifier
import net.minecraft.util.Formatting
import java.util.*

/**
 * DDI 网络包定义。使用 Bingo 的 PacketConverter 模式。
 */

/**
 * 进程级服务端数据包注册器。
 *
 * Fabric 负载编解码器是全局的，不能在每个游戏作用域中重复注册。
 * 将处理器保存在 Koin 单例中，可让服务端重启及连续多局游戏复用同一组注册。
 */
class DDIServerPackets(serverNetworking: IServerNetworking) {
    val wordSync = serverNetworking.registerS2C(DDIWordSyncPacket.V1)
    val triggered = serverNetworking.registerS2C(DDITriggeredPacket.V1)
    val teamSync = serverNetworking.registerS2C(DDITeamSyncPacket.V2)
    val teamTriggered = serverNetworking.registerS2C(DDITeamTriggeredPacket.V1)
    val stateReset = serverNetworking.registerS2C(DDIStateResetPacket.V1)
}

/** S2C：同步单个玩家的 DDI 词条和心数。 */
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

/** S2C：通知有人触发了词条。 */
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

/** S2C：每个 Bingo 队伍对应一份权威的共享词条、生命和计时器投影。 */
class DDITeamSyncPacket(
    val teamId: String,
    val teamName: String,
    val teamColor: Formatting,
    val memberNames: List<String>,
    val wordText: String,
    val hearts: Int,
    val maxHearts: Int,
    val timerSeconds: Int,
    val maxTimerSeconds: Int,
    val isEliminated: Boolean,
    val isOwnTeam: Boolean,
) {
    object V2 : PacketConverter<DDITeamSyncPacket> {
        override val id: Identifier = Identifier.of(MOD_ID_BINGO, "ddi_team_sync_v2")!!

        override fun fromPacketBuf(buf: IPacketBuf): DDITeamSyncPacket {
            return DDITeamSyncPacket(
                teamId = buf.readString(),
                teamName = buf.readString(),
                teamColor = runCatching { Formatting.valueOf(buf.readString()) }
                    .getOrDefault(Formatting.WHITE),
                memberNames = buf.readList(buf::readString),
                wordText = buf.readString(),
                hearts = buf.readInt(),
                maxHearts = buf.readInt(),
                timerSeconds = buf.readInt(),
                maxTimerSeconds = buf.readInt(),
                isEliminated = buf.readBoolean(),
                isOwnTeam = buf.readBoolean(),
            )
        }

        override fun toPacketBuf(source: DDITeamSyncPacket, dest: IPacketBuf) {
            dest.writeString(source.teamId)
            dest.writeString(source.teamName)
            dest.writeString(source.teamColor.name)
            dest.writeList(source.memberNames, dest::writeString)
            dest.writeString(source.wordText)
            dest.writeInt(source.hearts)
            dest.writeInt(source.maxHearts)
            dest.writeInt(source.timerSeconds)
            dest.writeInt(source.maxTimerSeconds)
            dest.writeBoolean(source.isEliminated)
            dest.writeBoolean(source.isOwnTeam)
        }
    }
}

/** S2C：某位成员改变了 Bingo 队伍的共享状态。 */
class DDITeamTriggeredPacket(
    val teamId: String,
    val teamName: String,
    val actorPlayerName: String,
    val wordText: String,
    val heartsRemaining: Int,
    val isElimination: Boolean,
    val isGain: Boolean,
) {
    object V1 : PacketConverter<DDITeamTriggeredPacket> {
        override val id: Identifier = Identifier.of(MOD_ID_BINGO, "ddi_team_triggered")!!

        override fun fromPacketBuf(buf: IPacketBuf): DDITeamTriggeredPacket {
            return DDITeamTriggeredPacket(
                teamId = buf.readString(),
                teamName = buf.readString(),
                actorPlayerName = buf.readString(),
                wordText = buf.readString(),
                heartsRemaining = buf.readInt(),
                isElimination = buf.readBoolean(),
                isGain = buf.readBoolean(),
            )
        }

        override fun toPacketBuf(source: DDITeamTriggeredPacket, dest: IPacketBuf) {
            dest.writeString(source.teamId)
            dest.writeString(source.teamName)
            dest.writeString(source.actorPlayerName)
            dest.writeString(source.wordText)
            dest.writeInt(source.heartsRemaining)
            dest.writeBoolean(source.isElimination)
            dest.writeBoolean(source.isGain)
        }
    }
}

/** S2C：重置 DDI HUD 状态（游戏结束时）。 */
class DDIStateResetPacket {
    object V1 : PacketConverter<DDIStateResetPacket> {
        override val id: Identifier = Identifier.of(MOD_ID_BINGO, "ddi_state_reset")!!

        override fun fromPacketBuf(buf: IPacketBuf): DDIStateResetPacket = DDIStateResetPacket()
        override fun toPacketBuf(source: DDIStateResetPacket, dest: IPacketBuf) {}
    }
}
