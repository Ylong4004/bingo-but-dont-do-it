package me.jfenn.bingo.platform.packet

import me.jfenn.bingo.platform.IPacketBuf
import net.minecraft.util.Identifier

interface PacketConverter<T> {

    val id: Identifier

    fun toPacketBuf(source: T, dest: IPacketBuf)

    fun fromPacketBuf(buf: IPacketBuf): T

}