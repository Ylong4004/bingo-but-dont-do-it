package me.jfenn.bingo.common.data

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import me.jfenn.bingo.common.MOD_ID_BINGO
import me.jfenn.bingo.common.card.data.ObjectiveData
import me.jfenn.bingo.common.card.data.ObjectiveDataReference
import me.jfenn.bingo.common.utils.jsonStrict
import net.minecraft.resource.ResourceManager
import org.slf4j.Logger

class ObjectiveLoader(
    private val log: Logger,
) {
    fun loadObjectives(
        manager: ResourceManager
    ): ObjectiveType {
        log.info("[ObjectiveLoader] Reloading BINGO objectives...")

        val newObjectives = mutableMapOf<String, ObjectiveData>()

        val startingPath = "$MOD_ID_BINGO/objectives"
        val lists = manager.findResources(startingPath) { it.path.endsWith(".json") }
        for ((id, file) in lists) {
            val objectiveId = id.namespace + ":" + id.path.removePrefix("$startingPath/").removeSuffix(".json")
            val objectiveData = try {
                file.inputStream.use {
                    @OptIn(ExperimentalSerializationApi::class)
                    jsonStrict.decodeFromStream<ObjectiveData>(it)
                }
            } catch (e: Throwable) {
                log.error("[ObjectiveLoader] Error reading objective $id: ${e.message}")
                log.debug("Error reading objective {}", id, e)
                continue
            }

            newObjectives[objectiveId] = objectiveData

            objectiveData.innerObjectiveEntries(objectiveId).forEach { (id, reference) ->
                if (reference is ObjectiveDataReference.Inline) {
                    newObjectives[id] = reference.data.apply { isRoot = false }
                }
            }
        }

        return newObjectives
    }
}