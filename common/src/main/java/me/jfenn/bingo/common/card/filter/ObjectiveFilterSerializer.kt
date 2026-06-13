package me.jfenn.bingo.common.card.filter

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
object ObjectiveFilterSerializer : KSerializer<ObjectiveFilterList> {
    override val descriptor: SerialDescriptor = buildSerialDescriptor("ObjectiveFilterList", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): ObjectiveFilterList {
        return ObjectiveFilterList.fromString(decoder.decodeString())
    }

    override fun serialize(encoder: Encoder, value: ObjectiveFilterList) {
        encoder.encodeString(value.toString())
    }
}
