package me.jfenn.bingo.common.data

import net.minecraft.resource.ResourceFinder
import net.minecraft.resource.ResourceManager
import org.slf4j.Logger

class ObtainableItemsLoader(
    private val log: Logger,
) {
    private fun collectItems(
        manager: ResourceManager
    ): Sequence<String> {
        val resources = sequence {
            ResourceFinder.json("loot_table")
                .findResources(manager)
                .let { yieldAll(it.entries) }
            ResourceFinder.json("recipe")
                .findResources(manager)
                .let { yieldAll(it.entries) }
        }

        return sequence {
            val itemRegex = Regex("\"([a-z0-9_.-]+:[a-z0-9_./-]+)\"")

            for ((_, resource) in resources) {
                resource.inputStream.use { stream ->
                    for (line in stream.bufferedReader().lines()) {
                        for (match in itemRegex.findAll(line)) {
                            val group = match.groupValues.getOrNull(1)
                            if (group != null) yield(group)
                        }
                    }
                }
            }
        }
    }

    fun loadObtainableItems(
        manager: ResourceManager,
    ): Set<String> {
        val obtainableItems = collectItems(manager).toSet()
        log.info("[ObtainableItemsLoader] Found ${obtainableItems.size} items referenced in resource files")
        return obtainableItems
    }
}