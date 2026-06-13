package me.jfenn.bingo.client.impl

import me.jfenn.bingo.client.platform.IResourcePackManager
import me.jfenn.bingo.common.MOD_ID_BINGO
import net.fabricmc.fabric.api.resource.ResourceManagerHelper
import net.fabricmc.fabric.api.resource.ResourcePackActivationType
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.util.Identifier
import org.slf4j.Logger

internal class ResourcePackManager(
    private val log: Logger,
) : IResourcePackManager {
    override fun register(identifier: String) {
        val namespace = identifier.substringBefore(':')
        val path = identifier.substringAfter(':')
        val success = ResourceManagerHelper.registerBuiltinResourcePack(
            Identifier.of(namespace, path)!!,
            FabricLoader.getInstance().getModContainer(MOD_ID_BINGO).orElseThrow(),
            ResourcePackActivationType.NORMAL
        )

        if (!success) log.error("Resource pack $identifier could not be registered!")
    }
}