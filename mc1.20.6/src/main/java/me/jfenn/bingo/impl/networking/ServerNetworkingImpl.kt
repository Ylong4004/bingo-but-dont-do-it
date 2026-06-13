package me.jfenn.bingo.impl.networking

import me.jfenn.bingo.impl.PlayerHandle
import me.jfenn.bingo.platform.event.IEventBus
import me.jfenn.bingo.platform.item.IItemStackFactory
import me.jfenn.bingo.platform.packet.*
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.server.network.ServerPlayerEntity
import org.slf4j.Logger

class ServerNetworkingImpl(
    private val log: Logger,
    private val itemStackFactory: IItemStackFactory,
    private val eventBus: IEventBus,
) : IServerNetworking {

    override fun <T> registerC2S(converter: PacketConverter<T>): IServerPacketHandlerC2S<T>
        = ServerPacketHandlerC2S(converter)

    override fun <T> registerS2C(converter: PacketConverter<T>): IServerPacketHandlerS2C<T>
        = ServerPacketHandlerS2C(converter)

    inner class ServerPacketHandlerC2S<T>(
        private val converter: PacketConverter<T>,
    ) : IServerPacketHandlerC2S<T> {
        override val name: String = "packetC2S=${converter.id}"

        private val id = CustomPayload.id<BingoPayload<T>>(converter.id.toString())
        private val codec: PacketCodec<RegistryByteBuf, BingoPayload<T>> = PacketCodec.of(
            { type: BingoPayload<T>, buf: RegistryByteBuf -> converter.toPacketBuf(type.value, PacketBufImpl(buf, itemStackFactory)) },
            { b: RegistryByteBuf -> BingoPayload(id, converter.fromPacketBuf(PacketBufImpl(b, itemStackFactory))) }
        )

        init {
            log.debug("[ServerNetworkingInfo] Registering C2S packet {}", converter.id.toString())
            try {
                PayloadTypeRegistry.playC2S().register(id, codec)
            } catch (e: IllegalArgumentException) {
                if (e.message?.contains("already registered") != true)
                    throw RuntimeException("Error initializing bingo ServerNetworking", e)
            }

            ServerPlayNetworking.registerGlobalReceiver(id) { packet, ctx ->
                val player = PlayerHandle(ctx.player())
                val data = ServerPacket(player, packet.value)
                ctx.player().server.execute {
                    eventBus.emit(this, data)
                }
            }
        }
    }

    inner class ServerPacketHandlerS2C<T>(
        private val converter: PacketConverter<T>,
    ) : IServerPacketHandlerS2C<T> {

        private val id = CustomPayload.id<BingoPayload<T>>(converter.id.toString())
        private val codec: PacketCodec<RegistryByteBuf, BingoPayload<T>> = PacketCodec.of(
            { type: BingoPayload<T>, buf: RegistryByteBuf -> converter.toPacketBuf(type.value, PacketBufImpl(buf, itemStackFactory)) },
            { b: RegistryByteBuf -> BingoPayload(id, converter.fromPacketBuf(PacketBufImpl(b, itemStackFactory))) }
        )

        init {
            log.debug("[ServerNetworkingInfo] Registering S2C packet {}", converter.id.toString())
            try {
                PayloadTypeRegistry.playS2C().register(id, codec)
            } catch (e: IllegalArgumentException) {
                if (e.message?.contains("already registered") != true)
                    throw RuntimeException("Error initializing bingo ServerNetworking", e)
            }
        }

        override fun isSupported(player: ServerPlayerEntity): Boolean {
            return ServerPlayNetworking.canSend(player, id)
        }

        override fun send(player: ServerPlayerEntity, packet: T): Boolean {
            if (isSupported(player)) {
                ServerPlayNetworking.send(player, BingoPayload(id, packet))
                return true
            } else {
                return false
            }
        }
    }
}
