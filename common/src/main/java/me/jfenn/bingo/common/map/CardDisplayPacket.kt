package me.jfenn.bingo.common.map

import me.jfenn.bingo.common.MOD_ID_BINGO
import me.jfenn.bingo.common.team.BingoTeamKey
import me.jfenn.bingo.common.utils.text.TextColor
import me.jfenn.bingo.platform.IPacketBuf
import me.jfenn.bingo.platform.packet.PacketConverter
import net.minecraft.util.Formatting
import net.minecraft.util.Identifier

data class CardDisplayPacket(
    val display: Map<BingoTeamKey?, CardDisplay>,
) {

    object V1 : PacketConverter<CardDisplayPacket> {
        override val id = Identifier.of("bingo", "display_cards")!!

        override fun toPacketBuf(source: CardDisplayPacket, dest: IPacketBuf) {
            val displaysWithKeys = source.display
                .mapNotNull { (teamKey, value) -> teamKey?.let {it to value } }
                .toMap()
            dest.writeList(displaysWithKeys.entries) { (key, display) ->
                dest.writeString(key.id)
                dest.writeString(
                    try {
                        // Previous versions use TextColor instead of Formatting, and crash if invalid values are received
                        TextColor.valueOf(display.teamColor?.name ?: "").name
                    } catch (e: IllegalArgumentException) {
                        TextColor.WHITE.name
                    }
                )
            }

            dest.writeList(source.display.keys) {
                dest.writeNullable(it?.id, dest::writeString)
            }
        }

        override fun fromPacketBuf(buf: IPacketBuf): CardDisplayPacket {
            val teamInfo = List(buf.readInt()) {
                BingoTeamKey(buf.readString()) to try {
                    Formatting.valueOf(buf.readString())
                } catch (e: IllegalArgumentException) {
                    Formatting.WHITE
                }
            }.toMap()

            val keys = List(buf.readInt()) {
                if (buf.readBoolean())
                    BingoTeamKey(buf.readString())
                else null
            }

            return CardDisplayPacket(
                display = keys.associateWith { key ->
                    CardDisplay(
                        teamColor = teamInfo[key],
                        players = emptyList(),
                    )
                },
            )
        }
    }

    object V2 : PacketConverter<CardDisplayPacket> {
        override val id = Identifier.of(MOD_ID_BINGO, "display_cards_v2")!!

        override fun toPacketBuf(source: CardDisplayPacket, dest: IPacketBuf) {
            dest.writeList(source.display.entries) {
                dest.writeNullable(it.key?.id, dest::writeString)
                CardDisplay.V1.toPacketBuf(it.value, dest)
            }
        }

        override fun fromPacketBuf(buf: IPacketBuf): CardDisplayPacket {
            val display = buf.readList {
                val key = buf.readNullable(buf::readString)?.let { BingoTeamKey(it) }
                val display = CardDisplay.V1.fromPacketBuf(buf)
                Pair(key, display)
            }

            return CardDisplayPacket(
                display = display.toMap()
            )
        }
    }

}