package me.jfenn.bingo.common.map

import me.jfenn.bingo.common.MOD_ID_BINGO
import me.jfenn.bingo.common.team.BingoTeamKey
import me.jfenn.bingo.platform.IPacketBuf
import me.jfenn.bingo.platform.packet.PacketConverter
import net.minecraft.util.Identifier

class CardTilesPacket(
    val teamKey: BingoTeamKey?,
    val tiles: Map<Int, CardTile>,
    val shouldNotify: Boolean = tiles.size <= 20,
) {

    object V1 : PacketConverter<CardTilesPacket> {
        override val id: Identifier = Identifier.of(MOD_ID_BINGO, "update_card_tiles")!!

        override fun fromPacketBuf(buf: IPacketBuf): CardTilesPacket {
            val teamKey = buf.readNullable(buf::readString)
                ?.let { BingoTeamKey(it) }
            val tiles = buf.readList {
                val index = buf.readInt()
                val tile = CardTile.V6.fromPacketBuf(buf)
                index to tile
            }.toMap()
            return CardTilesPacket(teamKey, tiles)
        }

        override fun toPacketBuf(source: CardTilesPacket, dest: IPacketBuf) {
            dest.writeNullable(source.teamKey?.id, dest::writeString)
            dest.writeList(source.tiles.entries) {
                dest.writeInt(it.key)
                CardTile.V6.toPacketBuf(it.value, dest)
            }
        }
    }

    object V2 : PacketConverter<CardTilesPacket> {
        override val id: Identifier = Identifier.of(MOD_ID_BINGO, "update_card_tiles_v2")!!

        override fun fromPacketBuf(buf: IPacketBuf): CardTilesPacket {
            val teamKey = buf.readNullable(buf::readString)
                ?.let { BingoTeamKey(it) }
            val tiles = buf.readList {
                val index = buf.readInt()
                val tile = CardTile.V6.fromPacketBuf(buf)
                index to tile
            }.toMap()
            val shouldNotify = buf.readBoolean()
            return CardTilesPacket(teamKey, tiles, shouldNotify)
        }

        override fun toPacketBuf(source: CardTilesPacket, dest: IPacketBuf) {
            dest.writeNullable(source.teamKey?.id, dest::writeString)
            dest.writeList(source.tiles.entries) {
                dest.writeInt(it.key)
                CardTile.V6.toPacketBuf(it.value, dest)
            }
            dest.writeBoolean(source.shouldNotify)
        }
    }
}