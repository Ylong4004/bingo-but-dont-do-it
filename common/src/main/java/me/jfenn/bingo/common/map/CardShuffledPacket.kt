package me.jfenn.bingo.common.map

import me.jfenn.bingo.common.MOD_ID_BINGO
import me.jfenn.bingo.common.team.BingoTeamKey
import me.jfenn.bingo.platform.IPacketBuf
import me.jfenn.bingo.platform.packet.PacketConverter
import net.minecraft.util.Identifier

class CardShuffledPacket(
    val teamKey: BingoTeamKey?
) {
    object V1 : PacketConverter<CardShuffledPacket> {
        override val id: Identifier = Identifier.of(MOD_ID_BINGO, "card_shuffled")!!

        override fun toPacketBuf(source: CardShuffledPacket, dest: IPacketBuf) {
            dest.writeNullable(source.teamKey?.id, dest::writeString)
        }

        override fun fromPacketBuf(buf: IPacketBuf): CardShuffledPacket {
            return CardShuffledPacket(
                teamKey = buf.readNullable { BingoTeamKey(buf.readString()) }
            )
        }
    }
}