package me.jfenn.bingo.common.utils

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.IntArraySerializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import me.jfenn.bingo.platform.block.BlockPosition

typealias BlockPositionType = @Serializable(with = BlockPositionSerializer::class) BlockPosition

object BlockPositionSerializer : KSerializer<BlockPosition> {
    private val innerSerializer = IntArraySerializer()
    override val descriptor = innerSerializer.descriptor

    override fun deserialize(decoder: Decoder): BlockPosition {
        val arr = innerSerializer.deserialize(decoder)
        return BlockPosition(arr[0], arr[1], arr[2])
    }

    override fun serialize(encoder: Encoder, value: BlockPosition) {
        innerSerializer.serialize(encoder, intArrayOf(value.x, value.y, value.z))
    }
}
