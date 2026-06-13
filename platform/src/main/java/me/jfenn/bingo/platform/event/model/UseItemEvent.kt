package me.jfenn.bingo.platform.event.model

import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.event.IReturnEvent
import me.jfenn.bingo.platform.item.IItemStack
import net.minecraft.server.MinecraftServer
import net.minecraft.util.Hand
import net.minecraft.world.World

class UseItemEvent(
    val server: MinecraftServer,
    val player: IPlayerHandle,
    val world: World,
    val hand: Hand,
) {
    companion object : IReturnEvent<UseItemEvent, ActionResult<IItemStack?>?>
}
