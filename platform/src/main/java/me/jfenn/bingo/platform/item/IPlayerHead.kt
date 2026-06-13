package me.jfenn.bingo.platform.item

import net.minecraft.server.network.ServerPlayerEntity

interface IPlayerHead : IItemStack {
    fun setSkullOwner(player: ServerPlayerEntity)
}