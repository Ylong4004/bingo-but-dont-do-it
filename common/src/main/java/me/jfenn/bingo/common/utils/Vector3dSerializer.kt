package me.jfenn.bingo.common.utils

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.DoubleArraySerializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.joml.Vector3d

typealias Vector3dAsArray = @Serializable(with = Vector3dSerializer::class) Vector3d

object Vector3dSerializer : KSerializer<Vector3d> {
    private val innerSerializer = DoubleArraySerializer()
    override val descriptor = innerSerializer.descriptor

    override fun deserialize(decoder: Decoder): Vector3d {
        val array = innerSerializer.deserialize(decoder)
        return Vector3d(array)
    }

    override fun serialize(encoder: Encoder, value: Vector3d) {
        val array = doubleArrayOf(value.x, value.y, value.z)
        innerSerializer.serialize(encoder, array)
    }
}