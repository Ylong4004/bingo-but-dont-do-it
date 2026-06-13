package me.jfenn.bingo.platform

interface IMapService {
    /**
     * Usually the map color bytes are in RGB...
     * ...but the last update swapped them to BGR, so now we're here
     */
    fun getMapColors(): Map<Byte, Int>

    fun getNextMapId(): Int
    fun createMapState(scale: Byte, locked: Boolean, world: IServerWorld): IMapState
    fun putMapState(id: Int, state: IMapState)
}

interface IMapState {
    fun setColor(x: Int, z: Int, color: Byte)
    /**
     * Uses System.arraycopy, which is a lot faster than manually setting each byte
     */
    fun copyFrom(source: ByteArray, destinationOffset: Int = 0, startIndex: Int = 0, endIndex: Int = source.size)
    fun markDirty(x: Int, z: Int)
}