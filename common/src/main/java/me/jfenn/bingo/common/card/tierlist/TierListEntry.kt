package me.jfenn.bingo.common.card.tierlist

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.jsonPrimitive

@Serializable(with = TierListEntry.Serializer::class)
class TierListEntry(
    val type: String?,
    val item: String,
) : Comparable<TierListEntry> {

    var listName: String = "[unknown]"
    var tierLabel: TierLabel? = null

    override fun hashCode(): Int {
        return item.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        return other is TierListEntry && other.item == item
    }

    override fun compareTo(other: TierListEntry): Int {
        return item.compareTo(other.item)
    }

    @OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
    companion object Serializer : KSerializer<TierListEntry> {

        override val descriptor: SerialDescriptor = buildSerialDescriptor("TierListEntry", PolymorphicKind.SEALED)

        private val stringSerializer get() = String.serializer()

        override fun deserialize(decoder: Decoder): TierListEntry {
            val element = (decoder as JsonDecoder).decodeJsonElement()
            return when {
                element is JsonArray -> TierListEntry(null, "")
                else -> {
                    val content = element.jsonPrimitive.content
                    TierListEntry(
                        type = content.substringBefore('!', missingDelimiterValue = "")
                            .takeIf { it.isNotEmpty() },
                        item = content.substringAfterLast('!')
                    )
                }
            }
        }

        override fun serialize(encoder: Encoder, value: TierListEntry) {
            stringSerializer.serialize(encoder, value.item)
        }
    }

}