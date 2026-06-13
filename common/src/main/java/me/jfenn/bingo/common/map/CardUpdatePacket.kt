@file:Suppress("Deprecation")

package me.jfenn.bingo.common.map

import me.jfenn.bingo.common.MOD_ID_BINGO
import me.jfenn.bingo.common.team.BingoTeamKey
import me.jfenn.bingo.platform.IPacketBuf
import me.jfenn.bingo.platform.packet.PacketConverter
import me.jfenn.bingo.platform.player.PlayerProfile
import net.minecraft.util.Formatting
import net.minecraft.util.Identifier
import java.util.*

@Deprecated("For backwards compatibility only. New versions use CardTilesPacket instead.")
class CardUpdatePacket(
    val view: CardView,
) {

    object V2 : PacketConverter<CardUpdatePacket> {
        override val id: Identifier = Identifier.of("bingo", "update_card_v2")!!

        override fun fromPacketBuf(buf: IPacketBuf): CardUpdatePacket {
            val teamKey = if (buf.readBoolean()) BingoTeamKey(buf.readString()) else null
            val teamColor = if (buf.readBoolean()) Formatting.valueOf(buf.readString()) else null
            val teamName = buf.readText()
            val tilesLen = buf.readInt()
            val tiles = MutableList(tilesLen) { CardTile.V2.fromPacketBuf(buf) }
            return CardUpdatePacket(
                CardView(
                    teamKey = teamKey,
                    display = CardDisplay(
                        teamColor = teamColor,
                        teamName = teamName,
                        players = emptyList(),
                    ),
                    tiles = tiles,
                )
            )
        }

        override fun toPacketBuf(source: CardUpdatePacket, dest: IPacketBuf) {
            val view = source.view

            if (view.teamKey != null) {
                dest.writeBoolean(true)
                dest.writeString(view.teamKey.id)
            } else {
                dest.writeBoolean(false)
            }

            if (view.display.teamColor != null) {
                dest.writeBoolean(true)
                dest.writeString(view.display.teamColor.name)
            } else {
                dest.writeBoolean(false)
            }

            dest.writeText(view.display.teamName)

            // read-only copy of tiles to avoid concurrent modification
            val tiles = view.tiles.toList()
            dest.writeInt(tiles.size)
            for (tile in tiles) {
                CardTile.V2.toPacketBuf(tile, dest)
            }
        }

    }

    object V3 : PacketConverter<CardUpdatePacket> {
        override val id: Identifier = Identifier.of("bingo", "update_card_v3")!!

        override fun fromPacketBuf(buf: IPacketBuf): CardUpdatePacket {
            val teamKey = if (buf.readBoolean()) BingoTeamKey(buf.readString()) else null
            val teamColor = if (buf.readBoolean()) Formatting.valueOf(buf.readString()) else null
            val teamName = buf.readText()
            val tiles = MutableList(buf.readInt()) { CardTile.V2.fromPacketBuf(buf) }
            val players = MutableList(buf.readInt()) {
                PlayerProfile(
                    uuid = UUID.fromString(buf.readString()),
                    name = buf.readString(),
                )
            }
            return CardUpdatePacket(
                CardView(
                    teamKey = teamKey,
                    display = CardDisplay(
                        teamColor = teamColor,
                        teamName = teamName,
                        players = players,
                    ),
                    tiles = tiles,
                )
            )
        }

        override fun toPacketBuf(source: CardUpdatePacket, dest: IPacketBuf) {
            val view = source.view

            if (view.teamKey != null) {
                dest.writeBoolean(true)
                dest.writeString(view.teamKey.id)
            } else {
                dest.writeBoolean(false)
            }

            if (view.display.teamColor != null) {
                dest.writeBoolean(true)
                dest.writeString(view.display.teamColor.name)
            } else {
                dest.writeBoolean(false)
            }

            dest.writeText(view.display.teamName)

            // read-only copy of tiles to avoid concurrent modification
            val tiles = view.tiles.toList()
            dest.writeInt(tiles.size)
            for (tile in tiles) {
                CardTile.V2.toPacketBuf(tile, dest)
            }

            dest.writeInt(view.display.players.size)
            for (player in view.display.players) {
                dest.writeString(player.uuid.toString())
                dest.writeString(player.name)
            }
        }
    }

    object V4 : PacketConverter<CardUpdatePacket> {
        override val id: Identifier = Identifier.of("bingo", "update_card_v4")!!

        override fun fromPacketBuf(buf: IPacketBuf): CardUpdatePacket {
            val teamKey = if (buf.readBoolean()) BingoTeamKey(buf.readString()) else null
            val teamColor = if (buf.readBoolean()) Formatting.valueOf(buf.readString()) else null
            val teamName = buf.readText()
            val tiles = MutableList(buf.readInt()) { CardTile.V3.fromPacketBuf(buf) }
            val players = MutableList(buf.readInt()) {
                PlayerProfile(
                    uuid = UUID.fromString(buf.readString()),
                    name = buf.readString(),
                )
            }
            return CardUpdatePacket(
                CardView(
                    teamKey = teamKey,
                    display = CardDisplay(
                        teamColor = teamColor,
                        teamName = teamName,
                        players = players,
                    ),
                    tiles = tiles,
                )
            )
        }

        override fun toPacketBuf(source: CardUpdatePacket, dest: IPacketBuf) {
            val view = source.view

            if (view.teamKey != null) {
                dest.writeBoolean(true)
                dest.writeString(view.teamKey.id)
            } else {
                dest.writeBoolean(false)
            }

            if (view.display.teamColor != null) {
                dest.writeBoolean(true)
                dest.writeString(view.display.teamColor.name)
            } else {
                dest.writeBoolean(false)
            }

            dest.writeText(view.display.teamName)

            // read-only copy of tiles to avoid concurrent modification
            val tiles = view.tiles.toList()
            dest.writeInt(tiles.size)
            for (tile in tiles) {
                CardTile.V3.toPacketBuf(tile, dest)
            }

            dest.writeInt(view.display.players.size)
            for (player in view.display.players) {
                dest.writeString(player.uuid.toString())
                dest.writeString(player.name)
            }
        }
    }

    object V5 : PacketConverter<CardUpdatePacket> {
        override val id: Identifier = Identifier.of("bingo", "update_card_v5")!!

        override fun fromPacketBuf(buf: IPacketBuf): CardUpdatePacket {
            val teamKey = if (buf.readBoolean()) BingoTeamKey(buf.readString()) else null
            val teamColor = if (buf.readBoolean()) Formatting.valueOf(buf.readString()) else null
            val teamName = buf.readText()
            val tiles = MutableList(buf.readInt()) { CardTile.V4.fromPacketBuf(buf) }
            val players = MutableList(buf.readInt()) {
                PlayerProfile(
                    uuid = UUID.fromString(buf.readString()),
                    name = buf.readString(),
                )
            }
            return CardUpdatePacket(
                CardView(
                    teamKey = teamKey,
                    display = CardDisplay(
                        teamColor = teamColor,
                        teamName = teamName,
                        players = players,
                    ),
                    tiles = tiles,
                )
            )
        }

        override fun toPacketBuf(source: CardUpdatePacket, dest: IPacketBuf) {
            val view = source.view

            if (view.teamKey != null) {
                dest.writeBoolean(true)
                dest.writeString(view.teamKey.id)
            } else {
                dest.writeBoolean(false)
            }

            if (view.display.teamColor != null) {
                dest.writeBoolean(true)
                dest.writeString(view.display.teamColor.name)
            } else {
                dest.writeBoolean(false)
            }

            dest.writeText(view.display.teamName)

            // read-only copy of tiles to avoid concurrent modification
            val tiles = view.tiles.toList()
            dest.writeInt(tiles.size)
            for (tile in tiles) {
                CardTile.V4.toPacketBuf(tile, dest)
            }

            dest.writeInt(view.display.players.size)
            for (player in view.display.players) {
                dest.writeString(player.uuid.toString())
                dest.writeString(player.name)
            }
        }
    }

    object V6 : PacketConverter<CardUpdatePacket> {
        override val id: Identifier = Identifier.of(MOD_ID_BINGO, "update_card_v6")!!

        override fun fromPacketBuf(buf: IPacketBuf): CardUpdatePacket {
            val teamKey = if (buf.readBoolean()) BingoTeamKey(buf.readString()) else null
            val teamColor = if (buf.readBoolean()) Formatting.valueOf(buf.readString()) else null
            val teamName = buf.readText()
            val tiles = MutableList(buf.readInt()) { CardTile.V5.fromPacketBuf(buf) }
            val players = MutableList(buf.readInt()) {
                PlayerProfile(
                    uuid = UUID.fromString(buf.readString()),
                    name = buf.readString(),
                )
            }
            return CardUpdatePacket(
                CardView(
                    teamKey = teamKey,
                    display = CardDisplay(
                        teamColor = teamColor,
                        teamName = teamName,
                        players = players,
                    ),
                    tiles = tiles,
                )
            )
        }

        override fun toPacketBuf(source: CardUpdatePacket, dest: IPacketBuf) {
            val view = source.view

            if (view.teamKey != null) {
                dest.writeBoolean(true)
                dest.writeString(view.teamKey.id)
            } else {
                dest.writeBoolean(false)
            }

            if (view.display.teamColor != null) {
                dest.writeBoolean(true)
                dest.writeString(view.display.teamColor.name)
            } else {
                dest.writeBoolean(false)
            }

            dest.writeText(view.display.teamName)

            // read-only copy of tiles to avoid concurrent modification
            val tiles = view.tiles.toList()
            dest.writeInt(tiles.size)
            for (tile in tiles) {
                CardTile.V5.toPacketBuf(tile, dest)
            }

            dest.writeInt(view.display.players.size)
            for (player in view.display.players) {
                dest.writeString(player.uuid.toString())
                dest.writeString(player.name)
            }
        }
    }

}