package me.jfenn.bingo.platform

import net.minecraft.util.Identifier
import java.nio.file.Path

interface ILevelStorage {
    fun getLevelSaveDir(worldId: Identifier): Path?
}