package me.jfenn.bingo.client.impl

import me.jfenn.bingo.client.platform.ClientPacket
import me.jfenn.bingo.client.platform.IClientNetworking
import me.jfenn.bingo.client.platform.IClientPacketHandlerC2S
import me.jfenn.bingo.client.platform.IClientPacketHandlerS2C
import me.jfenn.bingo.impl.networking.PacketBufImpl
import me.jfenn.bingo.platform.event.IEventBus
import me.jfenn.bingo.platform.item.IItemStackFactory
import me.jfenn.bingo.platform.packet.PacketConverter
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs

class ClientNetworkingImpl(
    private val itemStackFactory: IItemStackFactory,
    private val eventBus: IEventBus,
) : IClientNetworking {
    override fun <T> registerC2S(converter: PacketConverter<T>): IClientPacketHandlerC2S<T>
        = ClientPacketHandlerC2S(converter)

    override fun <T> registerS2C(converter: PacketConverter<T>): IClientPacketHandlerS2C<T>
        = ClientPacketHandlerS2C(converter)

    inner class ClientPacketHandlerC2S<T>(
        private val converter: PacketConverter<T>
    ) : IClientPacketHandlerC2S<T> {
        override fun isSupported(): Boolean {
            return ClientPlayNetworking.canSend(converter.id)
        }

        override fun send(packet: T): Boolean {
            if (isSupported()) {
                val buf = PacketByteBufs.create()
                converter.toPacketBuf(packet, PacketBufImpl(buf, itemStackFactory))
                ClientPlayNetworking.send(converter.id, buf)
                return true
            } else {
                return false
            }
        }
    }

    inner class ClientPacketHandlerS2C<T>(
        private val converter: PacketConverter<T>
    ) : IClientPacketHandlerS2C<T> {
        override val name: String = "packetS2C=${converter.id}"

        init {
            ClientPlayNetworking.registerGlobalReceiver(converter.id) { client, _, buf, _ ->
                val packet = converter.fromPacketBuf(PacketBufImpl(buf, itemStackFactory))
                val data = ClientPacket(packet)
                client.execute {
                    eventBus.emit(this, data)
                }
            }
        }
    }
}
