package me.jfenn.bingo.common.stats.packets

import kotlinx.serialization.encodeToString
import me.jfenn.bingo.platform.IPacketBuf
import me.jfenn.bingo.common.MOD_ID_BINGO
import me.jfenn.bingo.platform.packet.PacketConverter
import me.jfenn.bingo.common.stats.data.PlayerGameSummary
import me.jfenn.bingo.common.utils.json
import net.minecraft.util.Identifier

/**
 * Provides game statistics to be inserted into the recipient's database.
 */
data class StatsGamePacket(
    val game: PlayerGameSummary,
) {
    object V1 : PacketConverter<StatsGamePacket> {
        override val id = Identifier.of(MOD_ID_BINGO, "stats_game")!!

        override fun toPacketBuf(source: StatsGamePacket, dest: IPacketBuf) {
            dest.writeString(json.encodeToString(source.game))
        }

        override fun fromPacketBuf(buf: IPacketBuf): StatsGamePacket {
            return StatsGamePacket(
                game = json.decodeFromString(buf.readString())
            )
        }
    }
}