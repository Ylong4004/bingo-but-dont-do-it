package me.jfenn.bingo.platform.event.model

import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.event.IReturnEvent
import net.minecraft.server.MinecraftServer
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.world.World

class UseBlockEvent(
    val server: MinecraftServer,
    val player: IPlayerHandle,
    val world: World,
    val hand: Hand,
    val hit: BlockHitResult,
) {
    companion object : IReturnEvent<UseBlockEvent, ActionResult<Unit>?>
}
