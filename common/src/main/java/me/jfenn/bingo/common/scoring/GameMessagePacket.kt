package me.jfenn.bingo.common.scoring

import me.jfenn.bingo.common.MOD_ID_BINGO
import me.jfenn.bingo.common.map.CardTile
import me.jfenn.bingo.common.map.CardTileImage
import me.jfenn.bingo.common.team.BingoTeamKey
import me.jfenn.bingo.platform.IPacketBuf
import me.jfenn.bingo.platform.packet.PacketConverter
import me.jfenn.bingo.platform.readEnum
import me.jfenn.bingo.platform.text.ITextSerialized
import me.jfenn.bingo.platform.utils.UuidAsString
import net.minecraft.util.Identifier
import java.time.Duration

data class GameMessagePacket(
    val id: UuidAsString,
    val timeElapsed: Duration,
    val team: BingoTeamKey,
    val image: CardTileImage = CardTileImage.EMPTY,
    val imageList: List<CardTileImage> = emptyList(),
    val decoration: CardTile.Decoration?,
    val messageType: ScoreMessagePacket.MessageType,
    val message: ITextSerialized,
    val isUpdate: Boolean = false,
) {

    object V1 : PacketConverter<GameMessagePacket> {
        override val id: Identifier = Identifier.of(MOD_ID_BINGO, "game_message")!!

        override fun toPacketBuf(source: GameMessagePacket, dest: IPacketBuf) {
            dest.writeUUID(source.id)
            dest.writeDuration(source.timeElapsed)
            dest.writeString(source.team.id)

            CardTileImage.V1.toPacketBuf(source.image, dest)
            dest.writeList(source.imageList) {
                CardTileImage.V1.toPacketBuf(it, dest)
            }
            dest.writeNullable(source.decoration, dest::writeEnum)

            dest.writeEnum(source.messageType)
            dest.writeText(source.message)
            dest.writeBoolean(source.isUpdate)
        }

        override fun fromPacketBuf(buf: IPacketBuf): GameMessagePacket {
            val id = buf.readUUID()
            val timeElapsed = buf.readDuration()
            val team = BingoTeamKey(buf.readString())

            val image = CardTileImage.V1.fromPacketBuf(buf)
            val imageList = buf.readList { CardTileImage.V1.fromPacketBuf(buf) }
            val decoration = buf.readNullable { buf.readEnum<CardTile.Decoration>() }

            val messageType = buf.readEnum<ScoreMessagePacket.MessageType>()
            val message = buf.readText()

            val isUpdate = buf.readBoolean()

            return GameMessagePacket(
                id = id,
                timeElapsed = timeElapsed,
                team = team,
                image = image,
                imageList = imageList,
                decoration = decoration,
                messageType = messageType,
                message = message,
                isUpdate = isUpdate,
            )
        }
    }
}