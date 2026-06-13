package me.jfenn.bingo.common.scoring

import me.jfenn.bingo.common.MOD_ID_BINGO
import me.jfenn.bingo.common.map.CardTile
import me.jfenn.bingo.common.map.CardTileImage
import me.jfenn.bingo.common.team.BingoTeamKey
import me.jfenn.bingo.platform.IPacketBuf
import me.jfenn.bingo.platform.packet.PacketConverter
import me.jfenn.bingo.platform.player.PlayerProfile
import me.jfenn.bingo.platform.text.IText
import net.minecraft.util.Identifier
import java.time.Duration
import java.util.*

data class ScoreMessagePacket(
    val player: PlayerProfile?,
    val isViewerOnTeam: Boolean,
    val tile: CardTile?,
    val messageType: MessageType?,
    val message: IText,
) {
    enum class MessageType {
        LEADING_TEAM,
        LINE_SCORED,
        ITEM_SCORED,
        CARD_COMPLETED,
    }

    companion object {
        fun fromGameMessage(gameMessagePacket: GameMessagePacket, isViewerOnTeam: Boolean): ScoreMessagePacket {
            return ScoreMessagePacket(
                player = null,
                isViewerOnTeam = isViewerOnTeam,
                tile = CardTile(
                    image = gameMessagePacket.image,
                    imageList = gameMessagePacket.imageList,
                    decoration = gameMessagePacket.decoration,
                ),
                messageType = gameMessagePacket.messageType,
                message = gameMessagePacket.message,
            )
        }
    }

    fun toGameMessagePacket(): GameMessagePacket {
        return GameMessagePacket(
            id = UUID.randomUUID(),
            timeElapsed = Duration.ZERO,
            team = BingoTeamKey(""),
            image = tile?.image ?: CardTileImage.EMPTY,
            imageList = tile?.imageList.orEmpty(),
            decoration = tile?.decoration,
            messageType = messageType ?: MessageType.ITEM_SCORED,
            message = message,
        )
    }

    object V1 : PacketConverter<ScoreMessagePacket> {
        override val id: Identifier = Identifier.of(MOD_ID_BINGO, "score_message")!!

        override fun toPacketBuf(source: ScoreMessagePacket, dest: IPacketBuf) {
            if (source.player != null) {
                dest.writeBoolean(true)
                dest.writeString(source.player.uuid.toString())
                dest.writeString(source.player.name)
            } else {
                dest.writeBoolean(false)
            }

            dest.writeBoolean(source.isViewerOnTeam)

            if (source.tile?.image?.item != null) {
                dest.writeBoolean(true)
                dest.writeItemStack(source.tile.image.item)
            } else {
                dest.writeBoolean(false)
            }

            dest.writeString(source.messageType?.name ?: "")
            dest.writeText(source.message)
        }

        override fun fromPacketBuf(buf: IPacketBuf): ScoreMessagePacket {
            val player = if (buf.readBoolean()) {
                PlayerProfile(
                    uuid = UUID.fromString(buf.readString()),
                    name = buf.readString(),
                )
            } else null
            val isViewerOnTeam = buf.readBoolean()
            val item = if (buf.readBoolean()) buf.readItemStack() else null
            val messageType = try {
                MessageType.valueOf(buf.readString())
            } catch (e: Throwable) {
                null
            }
            val message = buf.readText()
            return ScoreMessagePacket(
                player = player,
                isViewerOnTeam = isViewerOnTeam,
                tile = CardTile(
                    image = CardTileImage(
                        item = item,
                        texture = null,
                    ),
                    decoration = null,
                ),
                messageType = messageType,
                message = message,
            )
        }
    }

    object V2 : PacketConverter<ScoreMessagePacket> {
        override val id: Identifier = Identifier.of(MOD_ID_BINGO, "score_message_v2")!!

        override fun toPacketBuf(source: ScoreMessagePacket, dest: IPacketBuf) {
            dest.writeNullable(source.player) {
                dest.writeString(it.uuid.toString())
                dest.writeString(it.name)
            }

            dest.writeBoolean(source.isViewerOnTeam)

            dest.writeNullable(source.tile?.image?.item, dest::writeItemStack)
            dest.writeNullable(source.tile?.image?.texture, dest::writeString)

            dest.writeString(source.messageType?.name ?: "")
            dest.writeText(source.message)
        }

        override fun fromPacketBuf(buf: IPacketBuf): ScoreMessagePacket {
            val player = buf.readNullable {
                PlayerProfile(
                    uuid = UUID.fromString(buf.readString()),
                    name = buf.readString(),
                )
            }
            val isViewerOnTeam = buf.readBoolean()
            val item = buf.readNullable(buf::readItemStack)
            val image = buf.readNullable(buf::readString)
            val messageType = try {
                MessageType.valueOf(buf.readString())
            } catch (e: Throwable) {
                null
            }
            val message = buf.readText()
            return ScoreMessagePacket(
                player = player,
                isViewerOnTeam = isViewerOnTeam,
                tile = CardTile(
                    image = CardTileImage(
                        item = item,
                        texture = image,
                    ),
                    decoration = null,
                ),
                messageType = messageType,
                message = message,
            )
        }
    }

    object V3 : PacketConverter<ScoreMessagePacket> {
        override val id: Identifier = Identifier.of(MOD_ID_BINGO, "score_message_v3")!!

        override fun toPacketBuf(source: ScoreMessagePacket, dest: IPacketBuf) {
            dest.writeNullable(source.player) {
                dest.writeString(it.uuid.toString())
                dest.writeString(it.name)
            }

            dest.writeBoolean(source.isViewerOnTeam)

            dest.writeNullable(source.tile) {
                CardTile.V5.toPacketBuf(it, dest)
            }

            dest.writeString(source.messageType?.name ?: "")
            dest.writeText(source.message)
        }

        override fun fromPacketBuf(buf: IPacketBuf): ScoreMessagePacket {
            val player = buf.readNullable {
                PlayerProfile(
                    uuid = UUID.fromString(buf.readString()),
                    name = buf.readString(),
                )
            }
            val isViewerOnTeam = buf.readBoolean()
            val tile = buf.readNullable {
                CardTile.V5.fromPacketBuf(buf)
            }
            val messageType = try {
                MessageType.valueOf(buf.readString())
            } catch (e: Throwable) {
                null
            }
            val message = buf.readText()
            return ScoreMessagePacket(
                player = player,
                isViewerOnTeam = isViewerOnTeam,
                tile = tile,
                messageType = messageType,
                message = message,
            )
        }
    }
}