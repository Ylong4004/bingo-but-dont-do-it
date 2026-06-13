package me.jfenn.bingo.client.impl

import me.jfenn.bingo.client.mixin.CreateWorldScreenAccessor
import net.minecraft.client.gui.screen.world.CreateWorldScreen

val CreateWorldScreen.accessor get() = this as CreateWorldScreenAccessor
