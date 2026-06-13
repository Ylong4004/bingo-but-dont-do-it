package me.jfenn.bingo.client.world

import me.jfenn.bingo.client.platform.IWorldService
import me.jfenn.bingo.common.BINGO_WORLD_PREFIX
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.world.CreateWorldScreen
import net.minecraft.server.MinecraftServer
import net.minecraft.util.WorldSavePath
import org.slf4j.Logger
import kotlin.io.path.name

class WorldServiceImpl(
    private val log: Logger,
    private val worldState: BingoWorldState,
) : IWorldService {

    private val client = MinecraftClient.getInstance()

    override fun createBingoWorld() {
        val parent = client.currentScreen
        worldState.state = ScreenState.CreateBingoWorld
        CreateWorldScreen.show(client) { client.setScreen(parent) }
    }

    override fun isBingoWorld(server: MinecraftServer): Boolean {
        return server.saveProperties.levelName.startsWith(BINGO_WORLD_PREFIX)
    }

    override fun deleteSave(server: MinecraftServer) {
        // If the game is not in progress, delete it!
        log.info("Deleting closed BINGO world save")

        val dirName = server.getSavePath(WorldSavePath.ROOT).parent.name
        client.levelStorage.createSessionWithoutSymlinkCheck(dirName).use {
            it.deleteSessionLock()
        }
    }

}