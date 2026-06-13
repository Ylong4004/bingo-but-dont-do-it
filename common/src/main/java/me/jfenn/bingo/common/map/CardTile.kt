package me.jfenn.bingo.common.map

import me.jfenn.bingo.common.BINGO_TEAM_PREFIX
import me.jfenn.bingo.common.MOD_ID_BINGO
import me.jfenn.bingo.common.card.tierlist.TierLabel
import me.jfenn.bingo.common.team.BingoTeamKey
import me.jfenn.bingo.platform.IPacketBuf
import me.jfenn.bingo.platform.packet.PacketConverter
import me.jfenn.bingo.platform.text.IText
import net.minecraft.util.Identifier
import java.time.Instant

data class CardTile(
    val id: String? = null,
    val image: CardTileImage,
    val imageList: List<CardTileImage> = emptyList(),
    val action: CardTileAction = image.item?.let { CardTileAction.Item(it) }
        ?: CardTileAction.None,
    val itemTier: TierLabel? = null,
    val name: IText? = image.item?.displayName,
    val lore: List<IText> = image.item?.lore ?: emptyList(),
    val decoration: Decoration?,
    val isHidden: Boolean = false,
    val isLocked: Boolean = false,
    /**
     * True if the tile should be rendered as flashing in the current tick
     */
    val isFlashingOnMap: Boolean = false,
    /**
     * True if the tile has been recently scored
     */
    val isFlashing: Boolean? = null,
    val isAchieved: Boolean = false,
    val progress: Float = 0f,
    val teamKeys: List<BingoTeamKey> = emptyList(),
) {

    @Transient
    var updatedAt: Instant? = null

    @Transient
    internal val mapRenderKey = image.item?.let { item ->
        MapRenderService.TileImageKey(
            name = item.identifier.toString(),
            image = image.mapTexture ?: image.texture,
            count = item.count,
            decoration = decoration,
        )
    }

    @Transient
    var clientTooltip: List<IText>? = null

    enum class Decoration {
        FREE_SPACE,
        ADVANCEMENT,
        ONE_OF,
        MANY_OF,
        @Deprecated("Use ONE_OF or MANY_OF instead!")
        MULTI_ITEM,
        FORBIDDEN,
        NONE
    }

    object V1 : PacketConverter<CardTile> {
        override val id: Identifier = Identifier.of(MOD_ID_BINGO, "card_tile")!!

        override fun fromPacketBuf(buf: IPacketBuf): CardTile {
            val item = if (buf.readBoolean()) {
                buf.readItemStack()
            } else null
            val  itemTier = if (buf.readBoolean()) {
                TierLabel.valueOf(buf.readString())
            } else null
            val isFreeSpace = buf.readBoolean()
            val isAdvancement = buf.readBoolean()
            val isMultiItem = buf.readBoolean()
            val isHidden = buf.readBoolean()
            val isLocked = buf.readBoolean()
            val isFlashing = buf.readBoolean()
            val isAchieved = buf.readBoolean()
            val teamKeys = List(buf.readInt()) {
                val color = buf.readString().lowercase()
                when (color) {
                    "light_purple" -> "pink"
                    "gold" -> "orange"
                    "dark_gray" -> "gray"
                    else -> color.removePrefix(BINGO_TEAM_PREFIX)
                }.let { BingoTeamKey(BINGO_TEAM_PREFIX + it) }
            }

            return CardTile(
                image = CardTileImage(
                    item = item,
                    texture = null,
                ),
                itemTier = itemTier,
                decoration = when {
                    isFreeSpace -> Decoration.FREE_SPACE
                    isAdvancement -> Decoration.ADVANCEMENT
                    isMultiItem -> @Suppress("Deprecation") Decoration.MULTI_ITEM
                    else -> null
                },
                isHidden = isHidden,
                isLocked = isLocked,
                isFlashingOnMap = isFlashing,
                isAchieved = isAchieved,
                teamKeys = teamKeys,
            )
        }

        override fun toPacketBuf(source: CardTile, dest: IPacketBuf) {
            if (source.image.item != null) {
                dest.writeBoolean(true)
                dest.writeItemStack(source.image.item)
            } else {
                dest.writeBoolean(false)
            }

            if (source.itemTier != null) {
                dest.writeBoolean(true)
                dest.writeString(source.itemTier.name)
            } else {
                dest.writeBoolean(false)
            }

            dest.writeBoolean(source.decoration == Decoration.FREE_SPACE)
            dest.writeBoolean(source.decoration == Decoration.ADVANCEMENT)
            dest.writeBoolean(source.decoration in MULTI_ITEM_ENUMS)
            dest.writeBoolean(source.isHidden)
            dest.writeBoolean(source.isLocked || source.decoration == Decoration.FORBIDDEN)
            dest.writeBoolean(source.isFlashingOnMap)
            dest.writeBoolean(source.isAchieved)

            dest.writeInt(source.teamKeys.size)
            for (team in source.teamKeys) {
                val formatting = when (
                    val color = team.label.uppercase()
                ) {
                    "PINK" -> "LIGHT_PURPLE"
                    "ORANGE" -> "GOLD"
                    "GRAY" -> "DARK_GRAY"
                    else -> color
                }
                dest.writeString(formatting)
            }
        }
    }

    object V2 : PacketConverter<CardTile> {
        override val id: Identifier = Identifier.of(MOD_ID_BINGO, "card_tile_v2")!!

        override fun fromPacketBuf(buf: IPacketBuf): CardTile {
            return V1.fromPacketBuf(buf)
        }

        override fun toPacketBuf(source: CardTile, dest: IPacketBuf) {
            if (source.image.item != null) {
                dest.writeBoolean(true)
                dest.writeItemStack(source.image.item)
            } else {
                dest.writeBoolean(false)
            }

            if (source.itemTier != null) {
                dest.writeBoolean(true)
                dest.writeString(source.itemTier.name)
            } else {
                dest.writeBoolean(false)
            }

            dest.writeBoolean(source.decoration == Decoration.FREE_SPACE)
            dest.writeBoolean(source.decoration == Decoration.ADVANCEMENT)
            dest.writeBoolean(source.decoration in MULTI_ITEM_ENUMS)
            dest.writeBoolean(source.isHidden)
            dest.writeBoolean(source.isLocked || source.decoration == Decoration.FORBIDDEN)
            dest.writeBoolean(source.isFlashingOnMap)
            dest.writeBoolean(source.isAchieved)

            dest.writeInt(source.teamKeys.size)
            for (team in source.teamKeys) {
                dest.writeString(team.id)
            }
        }
    }

    object V3 : PacketConverter<CardTile> {
        override val id: Identifier = Identifier.of(MOD_ID_BINGO, "card_tile_v3")!!

        override fun fromPacketBuf(buf: IPacketBuf): CardTile {
            return CardTile(
                image = CardTileImage(
                    item = buf.readNullable(buf::readItemStack),
                    texture = null,
                ),
                itemTier = if (buf.readBoolean()) {
                    TierLabel.valueOf(buf.readString())
                } else null,
                name = if (buf.readBoolean()) {
                    buf.readText()
                } else null,
                lore = List(buf.readInt()) { buf.readText() },
                decoration = try {
                    Decoration.valueOf(buf.readString())
                } catch (e: IllegalArgumentException) {
                    null
                },
                isHidden = buf.readBoolean(),
                isLocked = buf.readBoolean(),
                isFlashingOnMap = buf.readBoolean(),
                isAchieved = buf.readBoolean(),
                progress = buf.readFloat(),
                teamKeys = List(buf.readInt()) {
                    val color = buf.readString().lowercase()
                    when (color) {
                        "light_purple" -> "pink"
                        "gold" -> "orange"
                        "dark_gray" -> "gray"
                        else -> color.removePrefix(BINGO_TEAM_PREFIX)
                    }.let { BingoTeamKey(BINGO_TEAM_PREFIX + it) }
                },
            )
        }

        override fun toPacketBuf(source: CardTile, dest: IPacketBuf) {
            dest.writeNullable(source.image.item, dest::writeItemStack)

            if (source.itemTier != null) {
                dest.writeBoolean(true)
                dest.writeString(source.itemTier.name)
            } else {
                dest.writeBoolean(false)
            }

            if (source.name != null) {
                dest.writeBoolean(true)
                dest.writeText(source.name)
            } else {
                dest.writeBoolean(false)
            }

            dest.writeInt(source.lore.size)
            for (text in source.lore) {
                dest.writeText(text)
            }

            dest.writeString(when (source.decoration) {
                in MULTI_ITEM_ENUMS -> @Suppress("Deprecation") Decoration.MULTI_ITEM
                else -> source.decoration
            }?.name ?: "")

            dest.writeBoolean(source.isHidden)
            dest.writeBoolean(source.isLocked)
            dest.writeBoolean(source.isFlashingOnMap)
            dest.writeBoolean(source.isAchieved)

            dest.writeFloat(source.progress)

            dest.writeInt(source.teamKeys.size)
            for (team in source.teamKeys) {
                dest.writeString(team.id)
            }
        }
    }

    object V4 : PacketConverter<CardTile> {
        override val id: Identifier = Identifier.of(MOD_ID_BINGO, "card_tile_v4")!!

        override fun fromPacketBuf(buf: IPacketBuf): CardTile {
            val item = buf.readNullable(buf::readItemStack)
            val itemTier = buf.readNullable {
                TierLabel.valueOf(buf.readString())
            }
            val image = buf.readNullable(buf::readString)
            val name = buf.readNullable(buf::readText)
            val lore = List(buf.readInt()) { buf.readText() }
            val decoration = try {
                Decoration.valueOf(buf.readString())
            } catch (e: IllegalArgumentException) {
                null
            }
            val isHidden = buf.readBoolean()
            val isLocked = buf.readBoolean()
            val isFlashing = buf.readBoolean()
            val isAchieved = buf.readBoolean()
            val progress = buf.readFloat()
            val teamKeys = List(buf.readInt()) {
                val color = buf.readString().lowercase()
                when (color) {
                    "light_purple" -> "pink"
                    "gold" -> "orange"
                    "dark_gray" -> "gray"
                    else -> color.removePrefix(BINGO_TEAM_PREFIX)
                }.let { BingoTeamKey(BINGO_TEAM_PREFIX + it) }
            }
            return CardTile(
                image = CardTileImage(
                    item = item,
                    texture = image,
                ),
                itemTier = itemTier,
                name = name,
                lore = lore,
                decoration = decoration,
                isHidden = isHidden,
                isLocked = isLocked,
                isFlashingOnMap = isFlashing,
                isAchieved = isAchieved,
                progress = progress,
                teamKeys = teamKeys,
            )
        }

        override fun toPacketBuf(source: CardTile, dest: IPacketBuf) {
            dest.writeNullable(source.image.item, dest::writeItemStack)
            dest.writeNullable(source.itemTier?.name, dest::writeString)
            dest.writeNullable(source.image.texture, dest::writeString)
            dest.writeNullable(source.name, dest::writeText)

            dest.writeInt(source.lore.size)
            for (text in source.lore) {
                dest.writeText(text)
            }

            dest.writeString(when (source.decoration) {
                in MULTI_ITEM_ENUMS -> @Suppress("Deprecation") Decoration.MULTI_ITEM
                else -> source.decoration
            }?.name ?: "")

            dest.writeBoolean(source.isHidden)
            dest.writeBoolean(source.isLocked)
            dest.writeBoolean(source.isFlashingOnMap)
            dest.writeBoolean(source.isAchieved)

            dest.writeFloat(source.progress)

            dest.writeInt(source.teamKeys.size)
            for (team in source.teamKeys) {
                dest.writeString(team.id)
            }
        }
    }

    object V5 : PacketConverter<CardTile> {
        override val id: Identifier = Identifier.of(MOD_ID_BINGO, "card_tile_v5")!!

        override fun fromPacketBuf(buf: IPacketBuf): CardTile {
            val image = CardTileImage.V1.fromPacketBuf(buf)
            val imageList = buf.readList { CardTileImage.V1.fromPacketBuf(buf) }
            val itemTier = buf.readNullable {
                TierLabel.valueOf(buf.readString())
            }
            val name = buf.readNullable(buf::readText)
            val lore = List(buf.readInt()) { buf.readText() }
            val decoration = try {
                Decoration.valueOf(buf.readString())
            } catch (e: IllegalArgumentException) {
                null
            }
            val isHidden = buf.readBoolean()
            val isLocked = buf.readBoolean()
            val isFlashing = buf.readBoolean()
            val isAchieved = buf.readBoolean()
            val progress = buf.readFloat()
            val teamKeys = List(buf.readInt()) {
                BingoTeamKey(buf.readString())
            }
            return CardTile(
                image = image,
                imageList = imageList,
                itemTier = itemTier,
                name = name,
                lore = lore,
                decoration = decoration,
                isHidden = isHidden,
                isLocked = isLocked,
                isFlashingOnMap = isFlashing,
                isAchieved = isAchieved,
                progress = progress,
                teamKeys = teamKeys,
            )
        }

        override fun toPacketBuf(source: CardTile, dest: IPacketBuf) {
            CardTileImage.V1.toPacketBuf(source.image, dest)
            dest.writeList(source.imageList) {
                CardTileImage.V1.toPacketBuf(it, dest)
            }
            dest.writeNullable(source.itemTier?.name, dest::writeString)
            dest.writeNullable(source.name, dest::writeText)

            dest.writeInt(source.lore.size)
            for (text in source.lore) {
                dest.writeText(text)
            }

            dest.writeString(source.decoration?.name ?: "")

            dest.writeBoolean(source.isHidden)
            dest.writeBoolean(source.isLocked)
            dest.writeBoolean(source.isFlashingOnMap)
            dest.writeBoolean(source.isAchieved)

            dest.writeFloat(source.progress)

            dest.writeInt(source.teamKeys.size)
            for (team in source.teamKeys) {
                dest.writeString(team.id)
            }
        }
    }

    object V6 : PacketConverter<CardTile> {
        override val id: Identifier = Identifier.of(MOD_ID_BINGO, "card_tile_v6")!!

        override fun fromPacketBuf(buf: IPacketBuf): CardTile {
            val id = buf.readString().takeIf { it.isNotEmpty() }
            val image = CardTileImage.V1.fromPacketBuf(buf)
            val imageList = buf.readList { CardTileImage.V1.fromPacketBuf(buf) }
            val action = CardTileAction.V1.fromPacketBuf(buf)
            val itemTier = buf.readNullable {
                TierLabel.valueOf(buf.readString())
            }
            val name = buf.readNullable(buf::readText)
            val lore = List(buf.readInt()) { buf.readText() }
            val decoration = try {
                Decoration.valueOf(buf.readString())
            } catch (e: IllegalArgumentException) {
                null
            }
            val isHidden = buf.readBoolean()
            val isLocked = buf.readBoolean()
            val isFlashing = buf.readBoolean()
            val isAchieved = buf.readBoolean()
            val progress = buf.readFloat()
            val teamKeys = List(buf.readInt()) {
                BingoTeamKey(buf.readString())
            }
            return CardTile(
                id = id,
                image = image,
                imageList = imageList,
                action = action,
                itemTier = itemTier,
                name = name,
                lore = lore,
                decoration = decoration,
                isHidden = isHidden,
                isLocked = isLocked,
                isFlashing = isFlashing,
                isAchieved = isAchieved,
                progress = progress,
                teamKeys = teamKeys,
            )
        }

        override fun toPacketBuf(source: CardTile, dest: IPacketBuf) {
            dest.writeString(source.id.orEmpty())
            CardTileImage.V1.toPacketBuf(source.image, dest)
            dest.writeList(source.imageList) {
                CardTileImage.V1.toPacketBuf(it, dest)
            }
            CardTileAction.V1.toPacketBuf(source.action, dest)
            dest.writeNullable(source.itemTier?.name, dest::writeString)
            dest.writeNullable(source.name, dest::writeText)

            dest.writeInt(source.lore.size)
            for (text in source.lore) {
                dest.writeText(text)
            }

            dest.writeString(source.decoration?.name ?: "")

            dest.writeBoolean(source.isHidden)
            dest.writeBoolean(source.isLocked)
            dest.writeBoolean(source.isFlashing ?: false)
            dest.writeBoolean(source.isAchieved)

            dest.writeFloat(source.progress)

            dest.writeInt(source.teamKeys.size)
            for (team in source.teamKeys) {
                dest.writeString(team.id)
            }
        }
    }

    companion object {
        val EMPTY = CardTile(
            image = CardTileImage(
                item = null,
                texture = null,
            ),
            itemTier = null,
            decoration = null,
            isHidden = false,
            isLocked = false,
            isFlashing = false,
            isAchieved = false,
            teamKeys = emptyList(),
        )

        val MULTI_ITEM_ENUMS = arrayOf(Decoration.ONE_OF, Decoration.MANY_OF, @Suppress("Deprecation") Decoration.MULTI_ITEM)
    }
}
