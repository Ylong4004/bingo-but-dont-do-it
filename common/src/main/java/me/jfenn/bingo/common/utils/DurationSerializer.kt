package me.jfenn.bingo.common.utils

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Duration

typealias DurationType = @Serializable(with = DurationSerializer::class) Duration

object DurationSerializer : KSerializer<Duration> {
    @OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
    override val descriptor: SerialDescriptor = buildSerialDescriptor("java.time.Instant", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Duration {
        val str = decoder.decodeString()
        return Duration.parse(str)
    }

    override fun serialize(encoder: Encoder, value: Duration) {
        encoder.encodeString(value.toString())
    }
}
