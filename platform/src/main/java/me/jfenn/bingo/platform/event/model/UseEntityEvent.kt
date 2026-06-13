package me.jfenn.bingo.platform.event.model

import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.event.IReturnEvent
import net.minecraft.entity.Entity
import net.minecraft.server.MinecraftServer
import net.minecraft.util.Hand
import net.minecraft.util.hit.EntityHitResult
import net.minecraft.world.World

class UseEntityEvent(
    val server: MinecraftServer,
    val player: IPlayerHandle,
    val world: World,
    val hand: Hand,
    val entity: Entity,
    val hitResult: EntityHitResult?,
) {
    companion object : IReturnEvent<UseEntityEvent, ActionResult<Unit>?>
}
