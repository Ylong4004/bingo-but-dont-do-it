package me.jfenn.bingo.common.map

import me.jfenn.bingo.platform.IMapColorService
import kotlin.math.pow

fun rgb(r: Int, g: Int, b: Int): Int {
    return -0x1000000 or (r shl 16 and 0x00FF0000) or (g shl 8 and 0x0000FF00) or (b and 0x000000FF)
}

fun rgba(r: Int, g: Int, b: Int, a: Int): Int {
    return (a shl 24 and -0x1000000) or (r shl 16 and 0x00FF0000) or (g shl 8 and 0x0000FF00) or (b and 0x000000FF)
}

data class Color(val r: Int, val g: Int, val b: Int, val a: Int = 255) {
    fun map(mapper: (Int) -> Int): Color {
        return Color(
            mapper(this.r),
            mapper(this.g),
            mapper(this.b),
            this.a
        )
    }

    val asInt by lazy {
        rgb(r, g, b)
    }

    val asIntWithAlpha by lazy {
        rgba(r, g, b, a)
    }

    val asByte by lazy {
        asInt.closestColorByte()
    }

    fun mix(other: Color): Color {
        val alpha: Float = other.a / 255f
        return Color(
            ((r * (1-alpha)) + (other.r * alpha)).toInt(),
            ((g * (1-alpha)) + (other.g * alpha)).toInt(),
            ((b * (1-alpha)) + (other.b * alpha)).toInt(),
            a,
        )
    }

    companion object {
        val BLACK = fromInt(0xff000000.toInt())
        val TRANSPARENT = fromInt(0)

        fun fromInt(i: Int): Color {
            return Color(
                (i ushr 16) and 0xFF,
                (i ushr 8) and 0xFF,
                i and 0xFF,
                (i ushr 24) and 0xFF,
            )
        }

        fun fromString(string: String): Color {
            return string.removePrefix("#")
                .toUInt(16)
                .toInt()
                .let { fromInt(it) }
        }
    }
}

/**
 * Computes the visual "distance" between two color int values
 * using a low-cost approximation of visual color space:
 * https://stackoverflow.com/a/9085524
 */
fun Int.colorDistance(other: Int): Double {
    val (r, g, b, _) = Color.fromInt(this)
    val (r2, g2, b2, _) = Color.fromInt(other)
    val rMean = (r + r2) / 2
    val rDiff = r - r2
    val gDiff = g - g2
    val bDiff = b - b2
    return (((512 + rMean) * rDiff * rDiff) shr 8) + (4 * gDiff * gDiff) + (((767 - rMean) * bDiff * bDiff) shr 8).toDouble()
}


/**
 * Computes the visual "distance" approximation similar to what
 * is used in Spigot/Bukkit.
 */
fun Int.colorDistanceEuclidian(other: Int): Double {
    val (r, g, b, _) = Color.fromInt(this)
    val (r2, g2, b2, _) = Color.fromInt(other)
    val rMean = (r + r2) / 2
    val rWeight = 2 + rMean / 256
    val gWeight = 4.0
    val bWeight = 2 + (255 - rMean) / 256
    return (
        rWeight * (r - r2).toDouble().pow(2)
        + gWeight * (g - g2).toDouble().pow(2)
        + bWeight * (b - b2).toDouble().pow(2)
    )
}


/**
 * Computes the visual "distance" between two color int values
 * using the CDIDE2000 formula:
 * https://en.wikipedia.org/wiki/Color_difference#CIEDE2000
 */
/*fun Int.colorDistanceCDIDE2000(other: Int): Double {
    val color1Lab = ColorConversions.convertRGBtoXYZ(this)
        .let { ColorConversions.convertXYZtoCIELab(it) }
    val color1Lch = ColorConversions.convertCIELabtoCIELCH(color1Lab)

    val color2Lab = ColorConversions.convertRGBtoXYZ(other)
        .let { ColorConversions.convertXYZtoCIELab(it) }
    val color2Lch = ColorConversions.convertCIELabtoCIELCH(color2Lab)

    val k_l = 1
    val k_c = 1
    val k_h = 1

    val lAvg = (color1Lch.L + color2Lch.L) / 2
    val cAvg = (color1Lch.C + color2Lch.C) / 2

    val a1 = color1Lab.a + (color1Lab.a / 2) * (1 - sqrt(cAvg.pow(7) / (cAvg.pow(7) + 25.0.pow(7))))
    val a2 = color2Lab.a + (color2Lab.a / 2) * (1 - sqrt(cAvg.pow(7) / (cAvg.pow(7) + 25.0.pow(7))))

    val C1 = sqrt(a1.pow(2) + color1Lab.a.pow(2))
    val C2 = sqrt(a2.pow(2) + color2Lab.a.pow(2))

    val h1 = (Math.toDegrees(atan2(color1Lab.b, a1)) + 360.0) % 360.0
    val h2 = (Math.toDegrees(atan2(color2Lab.b, a2)) + 360.0) % 360.0

    val deltah = when {
        abs(h1 - h2) > 180 && h2 <= h1 -> h2 - h1 + 360
        abs(h1 - h2) > 180 && h2 > h1 -> h2 - h1 - 360
        else -> h2 - h1
    }

    val hAvg = when {
        C1 == 0.0 || C2 == 0.0 -> h1 + h2
        abs(h1 - h2) > 180 && h1 + h2 < 360 -> (h1 + h2 + 360) / 2
        abs(h1 - h2) > 180 && h1 + h2 >= 360 -> (h1 + h2 - 360) / 2
        else -> (h1 + h2) / 2
    }

    val deltaH = 2 * sqrt(C1 * C2) * sin(Math.toRadians(deltah / 2))

    val T = 1
        - 0.17 * cos(Math.toRadians(hAvg - 30))
        + 0.24 * cos(Math.toRadians(2 * hAvg))
        + 0.32 * cos(Math.toRadians(3 * hAvg + 6))
        - 0.20 * cos(Math.toRadians(4 * hAvg - 63))

    val S_L = 1 + ((0.015 * (lAvg - 50).pow(2)) / sqrt(20 + (lAvg - 50).pow(2)))
    val S_C = 1 + 0.045 * cAvg
    val S_H = 1 + 0.015 * cAvg * T

    val R_T = -2 * sqrt(cAvg.pow(7) / (cAvg.pow(7) + 25.0.pow(7))) * sin(Math.toRadians(60 * exp(-1 * ((hAvg - 275) / 25).pow(2))))

    val deltaE = sqrt(
        ((color2Lch.L - color1Lch.L) / (k_l * S_L)).pow(2)
            + ((C2 - C1) / (k_c * S_C)).pow(2)
            + (deltaH / (k_h * S_H)).pow(2)
            + R_T * ((C2 - C1) / (k_c * S_C)) * (deltaH / (k_h * S_H))
    )

    return deltaE
}*/

/**
 * Computes the visual "distance" between two color int values
 * using the CMC l:c algorithm:
 * https://en.wikipedia.org/wiki/Color_difference#CMC_l:c_(1984)
 */
/*fun Int.colorDistanceCMC1984(other: Int): Double {
    val color1Lab = ColorConversions.convertRGBtoXYZ(this)
        .let { ColorConversions.convertXYZtoCIELab(it) }
    val color1Lch = ColorConversions.convertCIELabtoCIELCH(color1Lab)

    val color2Lab = ColorConversions.convertRGBtoXYZ(other)
        .let { ColorConversions.convertXYZtoCIELab(it) }
    val color2Lch = ColorConversions.convertCIELabtoCIELCH(color2Lab)

    // Ratio of l:c (lightness/chroma) - using 1:1
    val l = 1
    val c = 1

    val T = when {
        color1Lch.h in 164.0..345.0 -> 0.56 + abs(0.2 * cos(Math.toRadians(color1Lch.h + 168)))
        else -> 0.36 + abs(0.4 * cos(Math.toRadians(color1Lch.h + 35)))
    }

    val F = sqrt(color1Lch.C.pow(4) / (color1Lch.C.pow(4) + 1900))

    val S_C = ((0.0638 * color1Lch.C) / (1 + 0.0131 * color1Lch.C)) + 0.638

    val S_L = when {
        color1Lch.L < 16 -> 0.511
        else -> (0.040975 * color1Lch.L) / (1 + 0.01765 * color1Lch.L)
    }

    val S_H = S_C * (F*T + 1 - F)

    val deltaH = sqrt(
        (color1Lab.a - color2Lab.a).pow(2)
        + (color1Lab.b - color2Lab.b).pow(2)
        - (color1Lch.C - color2Lch.C).pow(2)
    )

    val deltaE = sqrt(
        ((color2Lch.L - color1Lch.L) / (l * S_L)).pow(2)
            + ((color2Lch.C - color1Lch.C) / (c * S_C)).pow(2)
            + (deltaH / S_H).pow(2)
    )

    return deltaE
}*/

class MapColors(
    mapService: IMapColorService,
) {
    companion object {
        var mapColors = mapOf<Byte, Int>()
        const val BLACK = 116.toByte()
    }

    init {
        mapColors = mapService.getMapColors()
    }
}

fun Int.closestColorByte(): Byte {
    // colorDistanceCMC1984 fails on comparisons to #000... so we're doing this manually
    if (Color.fromInt(this).let { (r, g, b) -> r == 0 && g == 0 && b == 0 })
        return MapColors.BLACK

    return MapColors.mapColors.entries
        .minByOrNull { (_, renderColor) ->
            this.colorDistanceEuclidian(renderColor)
        }
        ?.key
        ?: MapColors.BLACK
}
