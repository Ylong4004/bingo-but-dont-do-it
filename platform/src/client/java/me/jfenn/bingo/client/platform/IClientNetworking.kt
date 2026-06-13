package me.jfenn.bingo.client.platform

import me.jfenn.bingo.platform.event.IEvent
import me.jfenn.bingo.platform.packet.PacketConverter

interface IClientNetworking {
    fun <T> registerC2S(converter: PacketConverter<T>): IClientPacketHandlerC2S<T>
    fun <T> registerS2C(converter: PacketConverter<T>): IClientPacketHandlerS2C<T>
}

interface IClientPacketHandlerC2S<T> {
    fun isSupported(): Boolean
    fun send(packet: T): Boolean
}

interface IClientPacketHandlerS2C<T> : IEvent<ClientPacket<T>>

data class ClientPacket<T>(
    val packet: T
)
