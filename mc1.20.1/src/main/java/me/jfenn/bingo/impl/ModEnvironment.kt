package me.jfenn.bingo.impl

import me.jfenn.bingo.platform.IModEnvironment
import net.fabricmc.api.EnvType
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Path

class ModEnvironment : IModEnvironment {
    override val configDir: Path
        get() = FabricLoader.getInstance().configDir

    override val gameDir: Path
        get() = FabricLoader.getInstance().gameDir

    override val envType: IModEnvironment.EnvType
        get() = when (FabricLoader.getInstance().environmentType!!) {
            EnvType.CLIENT -> IModEnvironment.EnvType.CLIENT
            EnvType.SERVER -> IModEnvironment.EnvType.SERVER
        }

    override fun isModLoaded(modId: String): Boolean =
        FabricLoader.getInstance().isModLoaded(modId)
}