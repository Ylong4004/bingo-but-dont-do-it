package me.jfenn.bingo.client.impl

import me.jfenn.bingo.client.platform.ISessionAccessor
import net.minecraft.client.MinecraftClient
import java.util.*

object SessionAccessor : ISessionAccessor {
    override fun getPlayerUuid(): UUID? {
        return MinecraftClient.getInstance().session.uuidOrNull
    }
}