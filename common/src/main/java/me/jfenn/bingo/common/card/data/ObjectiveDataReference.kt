package me.jfenn.bingo.common.card.data

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive

@Serializable(with = ObjectiveDataReferenceSerializer::class)
sealed class ObjectiveDataReference {
    @Serializable
    class Inline(
        val data: ObjectiveData,
    ) : ObjectiveDataReference()

    @Serializable
    data class Id(
        val id: String
    ) : ObjectiveDataReference()
}

@OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
object ObjectiveDataReferenceSerializer : KSerializer<ObjectiveDataReference> {
    override val descriptor: SerialDescriptor = buildSerialDescriptor("ObjectiveDataReference", PolymorphicKind.SEALED)

    private val baseSerializer get() = ObjectiveData.serializer()

    override fun deserialize(decoder: Decoder): ObjectiveDataReference {
        val element = (decoder as JsonDecoder).decodeJsonElement()
        return when {
            element is JsonPrimitive && element.isString -> ObjectiveDataReference.Id(element.content)
            else -> ObjectiveDataReference.Inline(decoder.json.decodeFromJsonElement(baseSerializer, element))
        }
    }

    override fun serialize(encoder: Encoder, value: ObjectiveDataReference) {
        require(encoder is JsonEncoder)
        when (value) {
            is ObjectiveDataReference.Id -> encoder.encodeString(value.id)
            is ObjectiveDataReference.Inline -> encoder.encodeJsonElement(encoder.json.encodeToJsonElement(baseSerializer, value.data))
        }
    }
}
