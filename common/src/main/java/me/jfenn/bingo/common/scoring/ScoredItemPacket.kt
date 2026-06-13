package me.jfenn.bingo.common.scoring

import me.jfenn.bingo.platform.IPacketBuf
import me.jfenn.bingo.common.MOD_ID_BINGO
import me.jfenn.bingo.platform.packet.PacketConverter
import me.jfenn.bingo.platform.player.PlayerProfile
import me.jfenn.bingo.common.team.BingoTeamKey
import me.jfenn.bingo.common.team.TeamScore
import net.minecraft.util.Formatting
import net.minecraft.util.Identifier
import java.util.*

data class ScoredItemPacket(
    val player: PlayerProfile?,
    val isViewerOnTeam: Boolean,
    val team: BingoTeamKey,
    val teamColor: Formatting,
    val score: TeamScore,
    val itemId: String?,
) {
    object V1 : PacketConverter<ScoredItemPacket> {
        override val id = Identifier.of(MOD_ID_BINGO, "score_update")!!

        override fun fromPacketBuf(buf: IPacketBuf): ScoredItemPacket {
            return ScoredItemPacket(
                player = PlayerProfile(
                    uuid = UUID.fromString(buf.readString()),
                    name = buf.readString(),
                ),
                isViewerOnTeam = buf.readBoolean(),
                team = BingoTeamKey(buf.readString()),
                teamColor = Formatting.valueOf(buf.readString()),
                score = TeamScore(
                    items = buf.readInt(),
                    lines = buf.readInt(),
                    cards = 0,
                ),
                itemId = if (buf.readBoolean()) { buf.readString() } else null,
            )
        }

        override fun toPacketBuf(source: ScoredItemPacket, dest: IPacketBuf) {
            // I could make a v2 packet to make this nullable...
            // but it isn't used anywhere outside of ScoreUpdateService yet
            val playerUuid = source.player?.uuid ?: UUID.fromString("00000000-0000-0000-0000-000000000000")
            val playerName = source.player?.name ?: ""
            dest.writeString(playerUuid.toString())
            dest.writeString(playerName)
            dest.writeBoolean(source.isViewerOnTeam)
            dest.writeString(source.team.id)
            dest.writeString(source.teamColor.name)

            dest.writeInt(source.score.items)
            dest.writeInt(source.score.lines)

            if (source.itemId != null) {
                dest.writeBoolean(true)
                dest.writeString(source.itemId)
            } else {
                dest.writeBoolean(false)
            }
        }
    }
}