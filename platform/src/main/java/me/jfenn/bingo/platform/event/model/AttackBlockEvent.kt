package me.jfenn.bingo.platform.event.model

import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.event.IReturnEvent
import net.minecraft.server.MinecraftServer
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.world.World

class AttackBlockEvent(
    val server: MinecraftServer,
    val player: IPlayerHandle,
    val world: World,
    val hand: Hand,
    val blockPos: BlockPos,
    val direction: Direction,
) {
    companion object : IReturnEvent<AttackBlockEvent, ActionResult<Unit>>
}
