package me.jfenn.bingo.common.map

import me.jfenn.bingo.common.MOD_ID
import me.jfenn.bingo.common.MOD_ID_BINGO
import me.jfenn.bingo.common.card.tierlist.TierLabel
import me.jfenn.bingo.common.config.readStream
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.team.BingoTeam
import me.jfenn.bingo.common.utils.LruHashMap
import me.jfenn.bingo.platform.IMapService
import me.jfenn.bingo.platform.IMapState
import me.jfenn.bingo.platform.IServerWorldFactory
import me.jfenn.bingo.platform.config.IConfigManager
import net.minecraft.util.Formatting
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.awt.image.BufferedImage
import java.io.InputStream
import javax.imageio.ImageIO
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime

internal class MapRenderService(
    private val log: Logger,
    private val worldFactory: IServerWorldFactory,
    private val mapService: IMapService,
    private val imageService: CardImageService,
    private val state: BingoState,
    private val configManager: IConfigManager,
) {

    companion object {
        private val COLOR_BACKGROUND = Color(130, 130, 130)
        private val COLOR_ITEM_SHADOW = Color(100, 100, 100)
        private val COLOR_MARGIN = Color(199, 199, 199)
        private val COLOR_SHADOW = Color(20, 20, 20)
        private val COLOR_ACHIEVED = Color(100, 255, 100)
        private val COLOR_FLASHING = Color(178, 76, 216)
        private val COLOR_ACHIEVED_SHADOW = Color(80, 120, 80)
        private val COLOR_LOCKED = Color(150, 0, 0)
        private val COLOR_WHITE = Color(255, 255, 255)

        private const val MAP_SIZE = 128
        private const val MAP_BORDER = 5

        private const val TILE_IMAGE = 16
        private const val TILE_PADDING = 3
        private const val TILE_MARGIN = 2
        private const val TILE_SIZE = TILE_IMAGE + TILE_PADDING*2 + TILE_MARGIN
        private const val IMAGE_SIZE = TILE_IMAGE + TILE_PADDING*2

        private fun BufferedImage.toMinecraftByteArray(): ByteArray {
            val arr = ByteArray(width*height) { 0.toByte() }
            for (x in 0 until width) for (y in 0 until height) {
                val color = Color.fromInt(getRGB(x, y))
                if (color.a != 0) {
                    arr[x + y * width] = color.asByte
                }
            }

            return arr
        }

        private val IMAGE_FRAME_ADVANCEMENT = MapRenderService::class.java
            .getResourceAsStream("/$MOD_ID_BINGO/image_frame_advancement.png")
            .use { ImageIO.read(it) }
            .toMinecraftByteArray()

        private val IMAGE_FRAME_FORBIDDEN = MapRenderService::class.java
            .getResourceAsStream("/$MOD_ID_BINGO/image_frame_forbidden.png")
            .use { ImageIO.read(it) }
            .toMinecraftByteArray()

        private const val NUMBER_WIDTH = 5
        private const val NUMBER_HEIGHT = 7
        private val NUMBERS = MapRenderService::class.java
            .getResourceAsStream("/$MOD_ID_BINGO/numbers.png")
            .use { ImageIO.read(it) }
            .toMinecraftByteArray()
    }

    fun validateItems(items: List<String>) {
        for (item in items) {
            try {
                readTileImage(item)!!.close()
            } catch (e: Throwable) {
                val log = LoggerFactory.getLogger(MOD_ID)
                log.warn("Missing item image: ${item.replace(':', '/')}.png")
            }
        }
    }

    private fun readTileImage(itemPath: String): InputStream? {
        val namespace = itemPath.substringBefore(':')
        val path = itemPath.substringAfter(':')
        return try {
            configManager.readStream("images/${path}.png", false) {
                MapRenderService::class.java.getResourceAsStream("/$MOD_ID_BINGO/images/${path}.png")!!
            }
        } catch (e: NullPointerException) {
            null
        } ?: try {
            configManager.readStream("images/$namespace/${path}.png", false) {
                MapRenderService::class.java.getResourceAsStream("/$MOD_ID_BINGO/images/$namespace/${path}.png")!!
            }
        } catch (e: NullPointerException) {
            null
        }
    }

    class BingoTileImage(
        val regular: ByteArray = ByteArray(IMAGE_SIZE*IMAGE_SIZE) { COLOR_BACKGROUND.asByte },
        val flashing: ByteArray = ByteArray(IMAGE_SIZE*IMAGE_SIZE) { COLOR_FLASHING.asByte },
        val achieved: ByteArray = ByteArray(IMAGE_SIZE*IMAGE_SIZE) { COLOR_ACHIEVED.asByte },
    ) {
        companion object {
            val EMPTY = BingoTileImage()

            val FALLBACK = MapRenderService::class.java.getResourceAsStream("/$MOD_ID_BINGO/image_fallback.png")
                    .use { ImageIO.read(it) }
                    .let { fromBufferedImage(it) }

            val HIDDEN = MapRenderService::class.java.getResourceAsStream("/$MOD_ID_BINGO/image_hidden.png")
                .use { ImageIO.read(it) }
                .let { fromBufferedImage(it) }

            val HIDDEN_TIERS = TierLabel.entries.associateWith { tier ->
                MapRenderService::class.java.getResourceAsStream("/$MOD_ID_BINGO/image_hidden_${tier.name.lowercase()}.png")
                    .use { ImageIO.read(it) }
                    .let { fromBufferedImage(it) }
            }

            fun fromBufferedImage(image: BufferedImage): BingoTileImage {
                val tileImage = BingoTileImage()

                for (x in 0 until IMAGE_SIZE) {
                    for (y in 0 until IMAGE_SIZE) {
                        val i = x + y * IMAGE_SIZE

                        val imgX = x - TILE_PADDING
                        val imgY = y - TILE_PADDING
                        val imageNearestX = imgX * image.width / TILE_IMAGE
                        val imageNearestY = imgY * image.height / TILE_IMAGE

                        var color = if (imageNearestX in 0 until image.width && imageNearestY in 0 until image.height) {
                            Color.fromInt(image.getRGB(imageNearestX, imageNearestY))
                        } else Color.fromInt(0)

                        val prevColor = if (imageNearestX-1 in 0 until image.width && imageNearestY-1 in 0 until image.height) {
                            Color.fromInt(image.getRGB(imageNearestX-1, imageNearestY-1))
                        } else Color.fromInt(0)

                        if (color.a == 0) {
                            if (prevColor.a != 0) {
                                tileImage.regular[i] = COLOR_ITEM_SHADOW.asByte
                                tileImage.flashing[i] = COLOR_ITEM_SHADOW.asByte
                                tileImage.achieved[i] = COLOR_ACHIEVED_SHADOW.asByte
                            }
                            continue
                        }

                        // if the image is transparent (glass), blend with the card background
                        if (color.a < 255) color = COLOR_BACKGROUND.mix(color.copy(a = (color.a * 2).coerceAtMost(255)))

                        tileImage.regular[i] = color.asByte
                        tileImage.flashing[i] = color.asByte
                        // make the item image slightly transparent when achieved
                        tileImage.achieved[i] = COLOR_ACHIEVED.mix(color.copy(a = 200)).asByte
                    }
                }

                return tileImage
            }
        }
    }

    data class TileImageKey(
        val name: String,
        val image: String?,
        val count: Int,
        val decoration: CardTile.Decoration?,
    )

    // keep a full card of 25 items in-memory, using LRU eviction
    // (could need extra space to handle rotating items)
    // worst case = all 8 teams each have their own card of 25 unique items
    private val imageMap = LruHashMap<TileImageKey, BingoTileImage?>(25*8)

    fun clearCache() = imageMap.clear()

    private inline fun drawTileDecorations(
        tile: TileImageKey,
        crossinline callback: (x: Int, y: Int, color: Byte) -> Unit,
    ) {
        // Draw the advancement frame if the tile is an advancement
        if (tile.decoration == CardTile.Decoration.ADVANCEMENT) {
            for (x in 0 until IMAGE_SIZE) for (y in 0 until IMAGE_SIZE) {
                val frameIndex = x + y * IMAGE_SIZE
                if (frameIndex !in IMAGE_FRAME_ADVANCEMENT.indices) continue
                val color = IMAGE_FRAME_ADVANCEMENT[frameIndex]
                if (color != 0.toByte()) {
                    callback(x, y, color)
                }
            }
        }

        // Draw a "+" if the tile can be captured multiple ways
        if (tile.decoration in CardTile.MULTI_ITEM_ENUMS) {
            val offset = 6
            for (innerT in offset-2..offset+2) {
                callback(offset, innerT, COLOR_WHITE.asByte)
                callback(innerT, offset, COLOR_WHITE.asByte)
            }
        }

        // Draw the locked "X" if the tile is forbidden
        if (tile.decoration == CardTile.Decoration.FORBIDDEN) {
            for (x in 0 until IMAGE_SIZE) for (y in 0 until IMAGE_SIZE) {
                val frameIndex = x + y * IMAGE_SIZE
                if (frameIndex !in IMAGE_FRAME_FORBIDDEN.indices) continue
                val color = IMAGE_FRAME_FORBIDDEN[frameIndex]
                if (color != 0.toByte()) {
                    callback(x, y, color)
                }
            }
        }

        // Draw the item count if more than 1
        if (tile.count > 1) {
            val digits = tile.count.toString()
                .map { it.digitToInt() }

            digits.forEachIndexed { index, digit ->
                val startX = 20 - (digits.size - index)*(NUMBER_WIDTH+1) + 1
                val startY = 20 - NUMBER_HEIGHT
                for (x in 0 until NUMBER_WIDTH) for (y in 0 until NUMBER_HEIGHT) {
                    val imageIndex = (digit * NUMBER_WIDTH) + x + (y * NUMBER_WIDTH * 10)
                    if (imageIndex !in NUMBERS.indices) continue

                    val drawX = startX + x
                    val drawY = startY + y
                    if (drawX !in 0..20) continue // y !in 0..20 is impossible (at least at the time of writing...)

                    val color = NUMBERS[imageIndex]
                    if (color != 0.toByte()) {
                        callback(drawX, drawY, color)
                        callback(drawX+1, drawY+1, COLOR_ITEM_SHADOW.asByte)
                    }
                }
            }
        }
    }

    private fun getTileImage(tile: CardTile): BingoTileImage? {
        val key = tile.mapRenderKey ?: return null

        if (imageMap.containsKey(key))
            return imageMap[key] ?: BingoTileImage.FALLBACK

        try {
            val imageStream = key.image
                ?.let { imageService.readCardImageStream(it) }
                ?: readTileImage(key.name)
                ?: return null

            val image = imageStream.use { ImageIO.read(it) }
            val tileImage = BingoTileImage.fromBufferedImage(image)

            drawTileDecorations(key) { x, y, color ->
                val index = x + y * IMAGE_SIZE
                tileImage.regular[index] = color
                tileImage.flashing[index] = color
                tileImage.achieved[index] = color
            }

            imageMap[key] = tileImage
            return tileImage
        } catch (e: Throwable) {
            log.error("Error reading item image: ${tile.image.item?.identifier}", e)
            imageMap[key] = null
            return BingoTileImage.FALLBACK
        }
    }

    private fun getMapColor(color: Formatting?): Byte {
        return when (color) {
            Formatting.AQUA -> 31
            Formatting.BLACK -> 29
            Formatting.BLUE -> 12
            Formatting.DARK_AQUA -> 23
            Formatting.DARK_BLUE -> 25
            Formatting.DARK_GRAY, Formatting.GRAY -> 21
            Formatting.DARK_GREEN -> 7
            Formatting.DARK_PURPLE -> 24
            Formatting.DARK_RED -> 28
            Formatting.GOLD -> 15
            Formatting.GREEN -> 33
            Formatting.LIGHT_PURPLE -> 20
            Formatting.RED -> 4
            Formatting.WHITE -> 14
            Formatting.YELLOW -> 30
            else -> 22
        }
    }

    private fun drawMapBorders(
        mapState: IMapState,
        team: BingoTeam?,
    ) {
        val borderLight = (getMapColor(team?.textColor)*4 + 2).toUByte().toByte()
        val borderMid = (getMapColor(team?.textColor)*4 + 1).toUByte().toByte()
        val borderDark = (getMapColor(team?.textColor)*4 + 3).toUByte().toByte()

        // Outer edge borders
        for (x in 0 until MAP_SIZE) {
            mapState.setColor(x, 0, borderLight)
            mapState.setColor(0, x, borderLight)

            mapState.setColor(x, MAP_SIZE-1, borderDark)
            mapState.setColor(MAP_SIZE-1, x, borderDark)
        }

        // Middle borders
        for (x in 1 until MAP_SIZE-1) {
            for (y in 1 until MAP_BORDER-1) {
                val yInv = MAP_SIZE - y - 1
                mapState.setColor(x, y, borderMid)
                mapState.setColor(y, x, borderMid)
                mapState.setColor(x, yInv, borderMid)
                mapState.setColor(yInv, x, borderMid)
            }
        }

        // Inner borders
        for (x in MAP_BORDER-1..MAP_SIZE-MAP_BORDER) {
            mapState.setColor(x, MAP_BORDER-1, borderDark)
            mapState.setColor(MAP_BORDER-1, x, borderDark)
            mapState.setColor(x, MAP_SIZE-MAP_BORDER, borderLight)
            mapState.setColor(MAP_SIZE-MAP_BORDER, x, borderLight)
        }

        // Cell borders
        for (x in MAP_BORDER until MAP_SIZE-MAP_BORDER) {
            for (y in 1..4) {
                val yActual = MAP_BORDER + y*TILE_SIZE - TILE_MARGIN + 1
                mapState.setColor(x, yActual, COLOR_SHADOW.asByte)
                mapState.setColor(yActual, x, COLOR_SHADOW.asByte)
            }
        }
        for (x in MAP_BORDER until MAP_SIZE-MAP_BORDER) {
            for (y in 1..4) {
                val yActual = MAP_BORDER + y*TILE_SIZE - TILE_MARGIN
                mapState.setColor(x, yActual, COLOR_MARGIN.asByte)
                mapState.setColor(yActual, x, COLOR_MARGIN.asByte)
            }
        }
    }

    private fun drawMapTile(
        mapState: IMapState,
        tile: CardTile,
        itemX: Int,
        itemY: Int,
    ) {
        if (itemX !in 0 until 5 || itemY !in 0 until 5) {
            log.error("drawMapTile() called with an invalid item position! ($itemX,$itemY)")
            return
        }

        var isRegularTile = false
        val tileImage = getTileImage(tile) ?: BingoTileImage.EMPTY
        val tileImageArray = when {
            tile.isHidden -> when {
                tile.itemTier != null -> {
                    BingoTileImage.HIDDEN_TIERS[tile.itemTier]?.regular ?: BingoTileImage.FALLBACK.regular
                }
                else -> BingoTileImage.HIDDEN.regular
            }
            tile.isFlashingOnMap -> tileImage.flashing
            tile.isAchieved -> tileImage.achieved
            else -> {
                isRegularTile = true
                tileImage.regular
            }
        }

        val tileX = MAP_BORDER + TILE_SIZE*itemX
        val tileY = MAP_BORDER + TILE_SIZE*itemY

        val innerMax = TILE_SIZE - TILE_MARGIN - 1
        for (innerY in 0..innerMax) {
            val y = tileY + innerY
            val mapStateStart = tileX + y * MAP_SIZE
            val tileImageStart = innerY * IMAGE_SIZE

            val arr = when {
                isRegularTile && innerY > innerMax * (1f - tile.progress) -> tileImage.flashing
                else -> tileImageArray
            }

            // Uses System.arraycopy, which is a lot faster than manually setting each byte
            mapState.copyFrom(arr, mapStateStart, tileImageStart, tileImageStart + IMAGE_SIZE)
        }

        if (tile.isLocked) {
            for (innerT in 0..innerMax) {
                val x = tileX + innerT
                val y = tileY + innerT
                mapState.setColor(x, y, COLOR_LOCKED.asByte)

                val yInv = tileY + innerMax - innerT
                mapState.setColor(x, yInv, COLOR_LOCKED.asByte)

                if (innerT > 0) {
                    mapState.setColor(x-1, y, COLOR_LOCKED.asByte)
                    mapState.setColor(x-1, yInv, COLOR_LOCKED.asByte)
                }
            }
        }

        val teams = tile.teamKeys.mapNotNull { state.teams[it] }
        if (teams.isNotEmpty()) {
            for (innerT in 0..innerMax*2 + 1) {
                val tileTeamX = innerT * teams.size / (innerMax*2 + 1)
                val itemTeam = teams.getOrNull(tileTeamX)?.mapColor ?: continue
                val variant = if (innerT % 2 == 0) 2 else 3
                val color = (getMapColor(itemTeam)*4 + variant).toUByte().toByte()

                if (innerT < innerMax) {
                    mapState.setColor(tileX + innerT, tileY, color)
                    mapState.setColor(tileX, tileY + innerT, color)
                } else {
                    mapState.setColor(tileX + innerT - innerMax, tileY + innerMax, color)
                    mapState.setColor(tileX + innerMax, tileY + innerT - innerMax, color)
                }
            }
        }

        for (x in tileX..tileX + innerMax) {
            for (y in tileY..tileY + innerMax) {
                // mark every pixel in the tile as dirty
                mapState.markDirty(x, y)
            }
        }
    }

    fun update(
        team: BingoTeam?,
        map: BingoMap,
        changedTiles: List<Int>,
    ) = measureTime {
        val state = map.mapState ?: kotlin.run {
            val state = mapService.createMapState(1, true, worldFactory.overworld)
            drawMapBorders(state, team)

            mapService.putMapState(map.mapId, state)
            map.mapState = state
            state
        }

        val view = map.view ?: return@measureTime

        for (index in changedTiles) {
            val tile = view.tiles.getOrNull(index) ?: continue
            val x = index % 5
            val y = index / 5
            drawMapTile(
                mapState = state,
                tile = tile,
                itemX = x,
                itemY = y,
            )
        }
    }.also {
        if (it >= 1.milliseconds) {
            log.info("Re-drawing map_${team?.map?.mapId} for team ${team?.id?.removePrefix("bingo_")} ($it)")
        }
    }

}