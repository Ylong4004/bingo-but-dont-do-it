package me.jfenn.bingo.impl

import me.jfenn.bingo.mixin.*
import net.minecraft.entity.player.HungerManager
import net.minecraft.item.map.MapState
import net.minecraft.server.MinecraftServer
import net.minecraft.util.thread.ThreadExecutor
import net.minecraft.village.raid.RaidManager

val HungerManager.accessor get() = this as HungerManagerAccessor
val RaidManager.raidManagerAccessor get() = this as RaidManagerAccessor
val MapState.accessor get() = this as MapStateAccessor
val MinecraftServer.accessor get() = this as MinecraftServerAccessor
val ThreadExecutor<*>.threadExecutorAccessor get() = this as ThreadExecutorAccessor
