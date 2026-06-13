package me.jfenn.bingo.platform

interface IMapColorService {
    /**
     * Usually the map color bytes are in RGB...
     * ...but the last update swapped them to BGR, so now we're here
     */
    fun getMapColors(): Map<Byte, Int>
}