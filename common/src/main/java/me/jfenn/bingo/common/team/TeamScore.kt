package me.jfenn.bingo.common.team

import kotlinx.serialization.Serializable
import me.jfenn.bingo.platform.text.IText
import me.jfenn.bingo.common.MOD_ID_BINGO
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.platform.IPacketBuf
import me.jfenn.bingo.platform.packet.PacketConverter
import net.minecraft.util.Identifier

@Serializable
data class TeamScore(
    val items: Int,
    val lines: Int,
    val cards: Int,
) {

    companion object {
        val ZERO = TeamScore(0, 0, 0)
    }

    fun formatText(text: TextProvider): IText {
        return buildList {
            if (cards > 0) add(text.cardCount(cards))
            if (lines > 0) add(text.lineCount(lines))
            if (items > 0) add(text.itemCount(items))
            if (isEmpty()) add(text.literal("0"))
        }.let {
            text.joinText(it)
        }
    }

    operator fun plus(other: TeamScore) = TeamScore(
        items = items + other.items,
        lines = lines + other.lines,
        cards = cards + other.cards,
    )

    object V1 : PacketConverter<TeamScore> {
        override val id: Identifier = Identifier.of(MOD_ID_BINGO, "team_score")!!

        override fun toPacketBuf(source: TeamScore, dest: IPacketBuf) {
            dest.writeInt(source.items)
            dest.writeInt(source.lines)
            dest.writeInt(source.cards)
        }

        override fun fromPacketBuf(buf: IPacketBuf): TeamScore {
            return TeamScore(
                items = buf.readInt(),
                lines = buf.readInt(),
                cards = buf.readInt(),
            )
        }
    }

}