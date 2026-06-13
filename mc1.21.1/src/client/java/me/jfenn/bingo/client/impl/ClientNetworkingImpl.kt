package me.jfenn.bingo.client.impl

import me.jfenn.bingo.client.platform.ClientPacket
import me.jfenn.bingo.client.platform.IClientNetworking
import me.jfenn.bingo.client.platform.IClientPacketHandlerC2S
import me.jfenn.bingo.client.platform.IClientPacketHandlerS2C
import me.jfenn.bingo.impl.networking.BingoPayload
import me.jfenn.bingo.impl.networking.PacketBufImpl
import me.jfenn.bingo.platform.event.IEventBus
import me.jfenn.bingo.platform.item.IItemStackFactory
import me.jfenn.bingo.platform.packet.PacketConverter
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import org.slf4j.Logger

class ClientNetworkingImpl(
    private val log: Logger,
    private val itemStackFactory: IItemStackFactory,
    private val eventBus: IEventBus,
) : IClientNetworking {

    override fun <T> registerC2S(converter: PacketConverter<T>): IClientPacketHandlerC2S<T>
        = ClientPacketHandlerC2S(converter)

    override fun <T> registerS2C(converter: PacketConverter<T>): IClientPacketHandlerS2C<T>
        = ClientPacketHandlerS2C(converter)

    inner class ClientPacketHandlerC2S<T>(
        private val converter: PacketConverter<T>,
    ) : IClientPacketHandlerC2S<T> {

        private val id = CustomPayload.id<BingoPayload<T>>(converter.id.let { "${it.namespace}_${it.path}" })
        private val codec: PacketCodec<RegistryByteBuf, BingoPayload<T>> = PacketCodec.of(
            { type: BingoPayload<T>, buf: RegistryByteBuf -> converter.toPacketBuf(type.value, PacketBufImpl(buf, itemStackFactory)) },
            { b: RegistryByteBuf -> BingoPayload(id, converter.fromPacketBuf(PacketBufImpl(b, itemStackFactory))) }
        )

        init {
            log.debug("[ClientNetworkingImpl] Registering C2S packet {}", converter.id.toString())
            try {
                PayloadTypeRegistry.playC2S().register(id, codec)
            } catch (e: IllegalArgumentException) {
                if (e.message?.contains("already registered") != true)
                    throw RuntimeException("Error initializing bingo ClientNetworking", e)
            }
        }

        override fun isSupported(): Boolean {
            return ClientPlayNetworking.canSend(id)
        }

        override fun send(packet: T): Boolean {
            if (isSupported()) {
                ClientPlayNetworking.send(BingoPayload(id, packet))
                return true
            } else {
                return false
            }
        }
    }

    inner class ClientPacketHandlerS2C<T>(
        private val converter: PacketConverter<T>,
    ) : IClientPacketHandlerS2C<T> {
        override val name: String = "packetS2C=${converter.id}"

        private val id = CustomPayload.id<BingoPayload<T>>(converter.id.let { "${it.namespace}_${it.path}" })
        private val codec: PacketCodec<RegistryByteBuf, BingoPayload<T>> = PacketCodec.of(
            { type: BingoPayload<T>, buf: RegistryByteBuf -> converter.toPacketBuf(type.value, PacketBufImpl(buf, itemStackFactory)) },
            { b: RegistryByteBuf -> BingoPayload(id, converter.fromPacketBuf(PacketBufImpl(b, itemStackFactory))) }
        )

        init {
            log.debug("[ClientNetworkingImpl] Registering S2C packet {}", converter.id.toString())
            try {
                PayloadTypeRegistry.playS2C().register(id, codec)
            } catch (e: IllegalArgumentException) {
                if (e.message?.contains("already registered") != true)
                    throw RuntimeException("Error initializing bingo ClientNetworking", e)
            }

            ClientPlayNetworking.registerGlobalReceiver(id) { packet, ctx ->
                val client = ctx.client()
                val data = ClientPacket(packet.value)
                client.execute {
                    eventBus.emit(this, data)
                }
            }
        }
    }
}
