package me.jfenn.bingo.impl

import me.jfenn.bingo.platform.IMapColorService
import net.minecraft.block.MapColor

class MapColorServiceImpl : me.jfenn.bingo.platform.IMapColorService {
    override fun getMapColors(): Map<Byte, Int> {
        return  (4 until 248)
            .associate { i ->
                val mapColorRGB = MapColor.getRenderColor(i)
                i.toUByte().toByte() to mapColorRGB
            }
    }
}