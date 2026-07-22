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
    val wordSync = serverNetworking.registerS2C(DDIWordSyncPacket.V2)
    val triggered = serverNetworking.registerS2C(DDITriggeredPacket.V1)
    val teamSync = serverNetworking.registerS2C(DDITeamSyncPacket.V3)
    val teamTriggered = serverNetworking.registerS2C(DDITeamTriggeredPacket.V1)
    val stateReset = serverNetworking.registerS2C(DDIStateResetPacket.V1)
    val accusationSync = serverNetworking.registerS2C(DDIAccusationSyncPacket.V1)
    val accusationOpen = serverNetworking.registerC2S(DDIAccusationOpenPacket.V1)
    val accusationVote = serverNetworking.registerC2S(DDIAccusationVotePacket.V1)
}

/** 客户端发起举报时只提交被举报玩家与公开槽位编号；服务端会重新校验全部资格。 */
class DDIAccusationOpenPacket(
    val accusedPlayerId: UUID,
    val slotIndex: Int,
) {
    object V1 : PacketConverter<DDIAccusationOpenPacket> {
        override val id: Identifier = Identifier.of(MOD_ID_BINGO, "ddi_accusation_open_v1")!!

        override fun fromPacketBuf(buf: IPacketBuf): DDIAccusationOpenPacket = DDIAccusationOpenPacket(
            accusedPlayerId = UUID(buf.readLong(), buf.readLong()),
            slotIndex = buf.readInt(),
        )

        override fun toPacketBuf(source: DDIAccusationOpenPacket, dest: IPacketBuf) {
            dest.writeLong(source.accusedPlayerId.mostSignificantBits)
            dest.writeLong(source.accusedPlayerId.leastSignificantBits)
            dest.writeInt(source.slotIndex)
        }
    }
}

/** 客户端投票请求；票权、重复票和票是否仍有效均由服务端裁决。 */
class DDIAccusationVotePacket(
    val voteId: UUID,
    val approve: Boolean,
) {
    object V1 : PacketConverter<DDIAccusationVotePacket> {
        override val id: Identifier = Identifier.of(MOD_ID_BINGO, "ddi_accusation_vote_v1")!!

        override fun fromPacketBuf(buf: IPacketBuf): DDIAccusationVotePacket = DDIAccusationVotePacket(
            voteId = UUID(buf.readLong(), buf.readLong()),
            approve = buf.readBoolean(),
        )

        override fun toPacketBuf(source: DDIAccusationVotePacket, dest: IPacketBuf) {
            dest.writeLong(source.voteId.mostSignificantBits)
            dest.writeLong(source.voteId.leastSignificantBits)
            dest.writeBoolean(source.approve)
        }
    }
}

/** 一场投票在单个客户端的安全展示投影；不含私密词条文本或其他玩家的投票身份。 */
data class DDIAccusationVoteView(
    val voteId: UUID,
    val accuserName: String,
    val accusedPlayerId: UUID,
    val accusedName: String,
    val slotIndex: Int,
    val yesVotes: Int,
    val noVotes: Int,
    val approvalThreshold: Int,
    val remainingTicks: Int,
    val canVote: Boolean,
    val ownVote: Int,
)

/** 服务端已过滤后的举报目标；客户端永远不自行推导举报资格。 */
data class DDIAccusationCandidateView(
    val accusedPlayerId: UUID,
    val accusedName: String,
    val slotIndex: Int,
)

/** S2C：Y 页面需要的进行中投票状态。 */
class DDIAccusationSyncPacket(
    val votes: List<DDIAccusationVoteView>,
    val candidates: List<DDIAccusationCandidateView>,
) {
    object V1 : PacketConverter<DDIAccusationSyncPacket> {
        override val id: Identifier = Identifier.of(MOD_ID_BINGO, "ddi_accusation_sync_v1")!!

        override fun fromPacketBuf(buf: IPacketBuf): DDIAccusationSyncPacket = DDIAccusationSyncPacket(
            votes = buf.readList {
                DDIAccusationVoteView(
                    voteId = UUID(buf.readLong(), buf.readLong()),
                    accuserName = buf.readString(),
                    accusedPlayerId = UUID(buf.readLong(), buf.readLong()),
                    accusedName = buf.readString(),
                    slotIndex = buf.readInt(),
                    yesVotes = buf.readInt(),
                    noVotes = buf.readInt(),
                    approvalThreshold = buf.readInt(),
                    remainingTicks = buf.readInt(),
                    canVote = buf.readBoolean(),
                    ownVote = buf.readInt(),
                )
            },
            candidates = buf.readList {
                DDIAccusationCandidateView(
                    accusedPlayerId = UUID(buf.readLong(), buf.readLong()),
                    accusedName = buf.readString(),
                    slotIndex = buf.readInt(),
                )
            },
        )

        override fun toPacketBuf(source: DDIAccusationSyncPacket, dest: IPacketBuf) {
            dest.writeList(source.votes) { vote ->
                dest.writeLong(vote.voteId.mostSignificantBits)
                dest.writeLong(vote.voteId.leastSignificantBits)
                dest.writeString(vote.accuserName)
                dest.writeLong(vote.accusedPlayerId.mostSignificantBits)
                dest.writeLong(vote.accusedPlayerId.leastSignificantBits)
                dest.writeString(vote.accusedName)
                dest.writeInt(vote.slotIndex)
                dest.writeInt(vote.yesVotes)
                dest.writeInt(vote.noVotes)
                dest.writeInt(vote.approvalThreshold)
                dest.writeInt(vote.remainingTicks)
                dest.writeBoolean(vote.canVote)
                dest.writeInt(vote.ownVote)
            }
            dest.writeList(source.candidates) { candidate ->
                dest.writeLong(candidate.accusedPlayerId.mostSignificantBits)
                dest.writeLong(candidate.accusedPlayerId.leastSignificantBits)
                dest.writeString(candidate.accusedName)
                dest.writeInt(candidate.slotIndex)
            }
        }
    }
}

/** 单条公开词条或私密倒计时的网络投影。 */
data class DDIWordSlotPacket(
    val index: Int,
    val wordText: String,
    val timerSeconds: Int,
    val maxTimerSeconds: Int,
)

private fun IPacketBuf.readDDIWordSlots(): List<DDIWordSlotPacket> = readList {
    DDIWordSlotPacket(
        index = readInt(),
        wordText = readString(),
        timerSeconds = readInt(),
        maxTimerSeconds = readInt(),
    )
}

private fun IPacketBuf.writeDDIWordSlots(slots: List<DDIWordSlotPacket>) {
    writeList(slots) { slot ->
        writeInt(slot.index)
        writeString(slot.wordText)
        writeInt(slot.timerSeconds)
        writeInt(slot.maxTimerSeconds)
    }
}

/** S2C：同步单个玩家的 DDI 词条槽位和共享生命。 */
class DDIWordSyncPacket(
    val playerId: UUID,
    val playerName: String,
    val slots: List<DDIWordSlotPacket>,
    val hearts: Int,
    val maxHearts: Int,
    val isEliminated: Boolean,
    val isSelf: Boolean,
) {
    object V2 : PacketConverter<DDIWordSyncPacket> {
        override val id: Identifier = Identifier.of(MOD_ID_BINGO, "ddi_word_sync_v2")!!

        override fun fromPacketBuf(buf: IPacketBuf): DDIWordSyncPacket {
            return DDIWordSyncPacket(
                playerId = UUID(buf.readLong(), buf.readLong()),
                playerName = buf.readString(),
                slots = buf.readDDIWordSlots(),
                hearts = buf.readInt(),
                maxHearts = buf.readInt(),
                isEliminated = buf.readBoolean(),
                isSelf = buf.readBoolean(),
            )
        }

        override fun toPacketBuf(source: DDIWordSyncPacket, dest: IPacketBuf) {
            dest.writeLong(source.playerId.mostSignificantBits)
            dest.writeLong(source.playerId.leastSignificantBits)
            dest.writeString(source.playerName)
            dest.writeDDIWordSlots(source.slots)
            dest.writeInt(source.hearts)
            dest.writeInt(source.maxHearts)
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

/** S2C：每个 Bingo 队伍对应一份权威的共享词条槽位和生命投影。 */
class DDITeamSyncPacket(
    val teamId: String,
    val teamName: String,
    val teamColor: Formatting,
    val memberNames: List<String>,
    val slots: List<DDIWordSlotPacket>,
    val hearts: Int,
    val maxHearts: Int,
    val isEliminated: Boolean,
    val isOwnTeam: Boolean,
) {
    object V3 : PacketConverter<DDITeamSyncPacket> {
        override val id: Identifier = Identifier.of(MOD_ID_BINGO, "ddi_team_sync_v3")!!

        override fun fromPacketBuf(buf: IPacketBuf): DDITeamSyncPacket {
            return DDITeamSyncPacket(
                teamId = buf.readString(),
                teamName = buf.readString(),
                teamColor = runCatching { Formatting.valueOf(buf.readString()) }
                    .getOrDefault(Formatting.WHITE),
                memberNames = buf.readList(buf::readString),
                slots = buf.readDDIWordSlots(),
                hearts = buf.readInt(),
                maxHearts = buf.readInt(),
                isEliminated = buf.readBoolean(),
                isOwnTeam = buf.readBoolean(),
            )
        }

        override fun toPacketBuf(source: DDITeamSyncPacket, dest: IPacketBuf) {
            dest.writeString(source.teamId)
            dest.writeString(source.teamName)
            dest.writeString(source.teamColor.name)
            dest.writeList(source.memberNames, dest::writeString)
            dest.writeDDIWordSlots(source.slots)
            dest.writeInt(source.hearts)
            dest.writeInt(source.maxHearts)
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
