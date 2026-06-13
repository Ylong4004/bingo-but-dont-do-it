package me.jfenn.bingo.common.utils

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.minecraft.util.Formatting

object FormattingSerializer : KSerializer<Formatting> {
    @OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
    override val descriptor: SerialDescriptor = buildSerialDescriptor("Formatting", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Formatting {
        val str = decoder.decodeString()
        return Formatting.valueOf(str)
    }

    override fun serialize(encoder: Encoder, value: Formatting) {
        encoder.encodeString(value.name)
    }
}
