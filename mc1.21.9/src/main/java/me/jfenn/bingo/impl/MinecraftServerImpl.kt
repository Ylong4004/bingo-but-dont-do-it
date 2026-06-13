package me.jfenn.bingo.impl

import me.jfenn.bingo.platform.ILevelStorage
import me.jfenn.bingo.platform.IMinecraftServer
import me.jfenn.bingo.platform.ITickManager
import net.minecraft.server.MinecraftServer
import net.minecraft.util.Identifier
import java.nio.file.Path

class MinecraftServerImpl(
    override val server: MinecraftServer,
): ITickManager, ILevelStorage, IMinecraftServer {
    override val isSingleplayer: Boolean
        get() = server.isSingleplayer

    override val isDedicated: Boolean
        get() = server.isDedicated

    override fun setFrozen(frozen: Boolean) {
        server.tickManager.isFrozen = frozen
    }

    override fun getLevelSaveDir(worldId: Identifier): Path? {
        val world = server.worlds.find { it.registryKey.value == worldId }
            ?: return null

        return server.accessor.session.getWorldDirectory(world.registryKey)
    }
}