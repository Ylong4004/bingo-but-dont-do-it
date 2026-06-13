package me.jfenn.bingo.common.map

import me.jfenn.bingo.platform.text.IText
import me.jfenn.bingo.common.MOD_ID_BINGO
import me.jfenn.bingo.common.team.BingoTeamKey
import me.jfenn.bingo.platform.IPacketBuf
import me.jfenn.bingo.platform.packet.PacketConverter
import me.jfenn.bingo.platform.player.PlayerProfile
import net.minecraft.util.Formatting
import net.minecraft.util.Identifier
import java.util.*

data class CardView(
    val teamKey: BingoTeamKey?,
    val display: CardDisplay,
    val tiles: MutableList<CardTile>,
) {

    fun tile(x: Int, y: Int): CardTile? {
        assert(x in 0 until 5) { "x=$x is not within the required 0..4 range" }
        assert(y in 0 until 5) { "y=$y is not within the required 0..4 range" }

        return tiles.getOrNull(x + y * 5)
    }

}

data class CardDisplay(
    val teamColor: Formatting? = null,
    val teamName: IText? = null,
    val players: List<PlayerProfile> = emptyList(),
) {

    /**
     * Client-side truncated team name (to fit within the card GUI)
     */
    @Transient
    var clientTeamName: IText? = null

    object V1 : PacketConverter<CardDisplay> {
        override val id: Identifier = Identifier.of(MOD_ID_BINGO, "card_view_display")!!

        override fun fromPacketBuf(buf: IPacketBuf): CardDisplay {
            return CardDisplay(
                teamColor = buf.readNullable {
                    try {
                        Formatting.valueOf(buf.readString())
                    } catch (e: IllegalArgumentException) {
                        null
                    }
                },
                teamName = buf.readText(),
                players = buf.readList {
                    PlayerProfile(
                        uuid = UUID.fromString(buf.readString()),
                        name = buf.readString(),
                    )
                }
            )
        }

        override fun toPacketBuf(source: CardDisplay, dest: IPacketBuf) {
            dest.writeNullable(source.teamColor?.name, dest::writeString)
            dest.writeText(source.teamName)
            dest.writeList(source.players) {
                dest.writeString(it.uuid.toString())
                dest.writeString(it.name)
            }
        }
    }
}