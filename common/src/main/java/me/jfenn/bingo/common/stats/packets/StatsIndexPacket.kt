package me.jfenn.bingo.common.stats.packets

import me.jfenn.bingo.platform.IPacketBuf
import me.jfenn.bingo.common.MOD_ID_BINGO
import me.jfenn.bingo.platform.packet.PacketConverter
import me.jfenn.bingo.common.stats.packets.StatsIndexPacket.Action
import net.minecraft.util.Identifier
import java.util.*

/**
 * Provides an index of known game IDs to the recipient for validation.
 *
 * If the recipient does not have a game listed in this packet,
 * they should send this back with [Action.REQUEST] to request those games.
 *
 * When a packet is received with [Action.REQUEST], the recipient should
 * fetch those game statistics and send a [StatsGamePacket] in response.
 */
data class StatsIndexPacket(
    val action: Action,
    val games: Set<UUID>,
) {
    enum class Action {
        BROADCAST,
        REQUEST,
    }

    object V1 : PacketConverter<StatsIndexPacket> {
        override val id = Identifier.of(MOD_ID_BINGO, "stats_index")!!

        override fun toPacketBuf(source: StatsIndexPacket, dest: IPacketBuf) {
            dest.writeInt(
                when (source.action) {
                    Action.BROADCAST -> 0
                    Action.REQUEST -> 1
                }
            )
            dest.writeInt(source.games.size)
            for (game in source.games) {
                dest.writeString(game.toString())
            }
        }

        override fun fromPacketBuf(buf: IPacketBuf): StatsIndexPacket {
            val action = when (buf.readInt()) {
                0 -> Action.BROADCAST
                else -> Action.REQUEST
            }
            val games = List(buf.readInt()) {
                UUID.fromString(buf.readString())
            }
            return StatsIndexPacket(action, games.toSet())
        }
    }
}