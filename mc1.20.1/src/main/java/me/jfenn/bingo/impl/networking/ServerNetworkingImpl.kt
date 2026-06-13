package me.jfenn.bingo.impl.networking

import me.jfenn.bingo.impl.PlayerHandle
import me.jfenn.bingo.platform.event.IEventBus
import me.jfenn.bingo.platform.item.IItemStackFactory
import me.jfenn.bingo.platform.packet.*
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.network.ServerPlayerEntity

class ServerNetworkingImpl(
    private val itemStackFactory: IItemStackFactory,
    private val eventBus: IEventBus,
) : IServerNetworking {
    override fun <T> registerC2S(converter: PacketConverter<T>): IServerPacketHandlerC2S<T>
        = ServerPacketHandlerC2S(converter)

    override fun <T> registerS2C(converter: PacketConverter<T>): IServerPacketHandlerS2C<T>
        = ServerPacketHandlerS2C(converter)

    inner class ServerPacketHandlerC2S<T>(
        private val converter: PacketConverter<T>
    ) : IServerPacketHandlerC2S<T> {
        override val name: String = "packetC2S=${converter.id}"

        init {
            ServerPlayNetworking.registerGlobalReceiver(converter.id) { server, player, _, buf, _ ->
                val packet = converter.fromPacketBuf(PacketBufImpl(buf, itemStackFactory))
                val data = ServerPacket(PlayerHandle(player), packet)
                server.execute {
                    eventBus.emit(this, data)
                }
            }
        }
    }

    inner class ServerPacketHandlerS2C<T>(
        private val converter: PacketConverter<T>
    ) : IServerPacketHandlerS2C<T> {
        override fun isSupported(player: ServerPlayerEntity): Boolean {
            return ServerPlayNetworking.canSend(player, converter.id)
        }

        override fun send(player: ServerPlayerEntity, packet: T): Boolean {
            if (isSupported(player)) {
                val buf = PacketByteBufs.create()
                converter.toPacketBuf(packet, PacketBufImpl(buf, itemStackFactory))
                ServerPlayNetworking.send(player, converter.id, buf)
                return true
            } else {
                return false
            }
        }
    }
}