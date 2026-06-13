package me.jfenn.bingo.impl

import com.mojang.serialization.Codec
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.modules.SerializersModule
import me.jfenn.bingo.platform.IJsonSerializers
import me.jfenn.bingo.platform.item.IItemStack
import me.jfenn.bingo.platform.item.IItemStackFactory
import me.jfenn.bingo.platform.text.IText
import net.minecraft.item.ItemStack
import net.minecraft.server.MinecraftServer
import net.minecraft.text.TextCodecs
import kotlin.reflect.KClass

class JsonSerializers(
    private val itemStackFactory: IItemStackFactory,
    server: MinecraftServer?,
) : IJsonSerializers {

    private val registryOps = server?.registryManager?.getOps(JsonOpsKotlinx) ?: JsonOpsKotlinx

    inner class CodecSerializer<T: Any>(
        private val codec: Codec<T>,
        kClass: KClass<T>,
    ) : KSerializer<T> {
        @OptIn(InternalSerializationApi::class)
        override val descriptor: SerialDescriptor = buildSerialDescriptor("CodecSerializer(${kClass.qualifiedName})", SerialKind.CONTEXTUAL)

        override fun deserialize(decoder: Decoder): T {
            require(decoder is JsonDecoder)
            val jsonElement = decoder.decodeJsonElement()
            return codec.parse(registryOps, jsonElement).getOrThrow()
        }

        override fun serialize(encoder: Encoder, value: T) {
            require(encoder is JsonEncoder)
            val jsonElement = codec.encodeStart(registryOps, value).getOrThrow()
            encoder.encodeJsonElement(jsonElement)
        }
    }

    private val module = SerializersModule {
        contextual(IText::class, CodecSerializer(
            codec = TextCodecs.CODEC.xmap(
                { TextImpl(it.copy()) },
                { it.value }
            ),
            kClass = IText::class,
        ))

        contextual(IItemStack::class, CodecSerializer(
            codec = ItemStack.OPTIONAL_CODEC.xmap(
                { itemStackFactory.forStack(it) },
                { it.stack }
            ),
            kClass = IItemStack::class,
        ))
    }

    override val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        prettyPrint = false
        serializersModule = module
    }
}