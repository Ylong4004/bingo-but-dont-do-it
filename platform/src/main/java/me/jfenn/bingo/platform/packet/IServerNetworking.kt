package me.jfenn.bingo.platform.packet

import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.event.IEvent
import net.minecraft.server.network.ServerPlayerEntity

interface IServerNetworking {
    fun <T> registerC2S(converter: PacketConverter<T>): IServerPacketHandlerC2S<T>
    fun <T> registerS2C(converter: PacketConverter<T>): IServerPacketHandlerS2C<T>
}

interface IServerPacketHandlerC2S<T> : IEvent<ServerPacket<T>>

interface IServerPacketHandlerS2C<T> {
    fun isSupported(player: IPlayerHandle): Boolean = isSupported(player.player)
    fun isSupported(player: ServerPlayerEntity): Boolean
    fun send(player: IPlayerHandle, packet: T) = send(player.player, packet)
    fun send(player: ServerPlayerEntity, packet: T): Boolean
}

data class ServerPacket<T>(
    val player: IPlayerHandle,
    val packet: T
)
