package me.jfenn.bingo.impl.networking

import net.minecraft.network.packet.CustomPayload

class BingoPayload<T>(
    private val id: CustomPayload.Id<BingoPayload<T>>,
    val value: T,
) : CustomPayload {
    override fun getId() = id
}
