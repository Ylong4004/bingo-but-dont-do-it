package me.jfenn.bingo.common.utils

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.awt.Color

typealias ColorType = @Serializable(with = ColorSerializer::class) Color

object ColorSerializer : KSerializer<Color> {
    override val descriptor = PrimitiveSerialDescriptor("java.awt.Color", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Color {
        val str = decoder.decodeString().removePrefix("#")
        val hasAlpha = str.length == 4 || str.length == 8
        return Color(Integer.parseUnsignedInt(str, 16), hasAlpha)
    }

    override fun serialize(encoder: Encoder, value: Color) {
        val hex = value.rgb.toUInt().toLong()
            .toString(16)
            .padStart(8, '0')

        encoder.encodeString("#$hex")
    }
}