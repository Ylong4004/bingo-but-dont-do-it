package me.jfenn.bingo.client.impl

import me.jfenn.bingo.client.mixin.CreateWorldScreenAccessor
import me.jfenn.bingo.client.mixin.DrawContextAccessor
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.world.CreateWorldScreen

val CreateWorldScreen.accessor get() = this as CreateWorldScreenAccessor
val DrawContext.accessor get() = this as DrawContextAccessor
