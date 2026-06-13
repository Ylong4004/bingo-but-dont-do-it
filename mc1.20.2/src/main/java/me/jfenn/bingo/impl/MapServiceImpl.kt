package me.jfenn.bingo.impl

import me.jfenn.bingo.platform.IMapService
import me.jfenn.bingo.platform.IMapState
import me.jfenn.bingo.platform.IServerWorld
import net.minecraft.block.MapColor
import net.minecraft.item.FilledMapItem
import net.minecraft.item.map.MapState
import net.minecraft.server.MinecraftServer

class MapServiceImpl(
    private val server: MinecraftServer,
) : IMapService {
    override fun getMapColors(): Map<Byte, Int> {
        return  (4 until 248)
            .associate { i ->
                val mapColorRGB = MapColor.getRenderColor(i)
                i.toUByte().toByte() to mapColorRGB
            }
    }

    override fun getNextMapId(): Int {
        return server.overworld.nextMapId
    }

    override fun createMapState(scale: Byte, locked: Boolean, world: IServerWorld): IMapState {
        require(world is ServerWorldImpl)
        val state = MapState.of(scale, locked, world.world.registryKey)
        return MapStateImpl(state)
    }

    override fun putMapState(id: Int, state: IMapState) {
        require(state is MapStateImpl)
        server.overworld.putMapState(FilledMapItem.getMapName(id), state.state)
    }
}

class MapStateImpl(
    val state: MapState,
) : IMapState {
    override fun setColor(x: Int, z: Int, color: Byte) {
        state.setColor(x, z, color)
    }

    override fun copyFrom(source: ByteArray, destinationOffset: Int, startIndex: Int, endIndex: Int) {
        source.copyInto(state.colors, destinationOffset, startIndex, endIndex)
    }

    override fun markDirty(x: Int, z: Int) {
        state.accessor.invokeMarkDirty(x, z)
    }
}
