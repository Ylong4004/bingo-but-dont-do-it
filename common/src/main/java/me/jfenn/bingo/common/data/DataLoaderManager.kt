package me.jfenn.bingo.common.data

import me.jfenn.bingo.common.controller.DatapackFunctionService
import me.jfenn.bingo.common.spawn.SpawnKitLoader
import me.jfenn.bingo.common.text.MessageService
import me.jfenn.bingo.platform.event.IEventBus
import me.jfenn.bingo.platform.event.model.ReloadEvent
import org.slf4j.Logger
import java.util.concurrent.CompletableFuture
import kotlin.time.TimeSource

internal class DataLoaderManager(
    events: IEventBus,
    private val log: Logger,
    private val scopedData: ScopedData,
    private val filterPresetsLoader: FilterPresetsLoader,
    private val objectiveLoader: ObjectiveLoader,
    private val spawnKitLoader: SpawnKitLoader,
    private val tierListLoader: TierListLoader,
    private val tagLoader: TagLoader,
    private val teamPresetLoader: TeamPresetLoader,
    private val obtainableItemsLoader: ObtainableItemsLoader,
    private val datapackFunctionLoader: DatapackFunctionService.Loader,
    private val messageLoader: MessageService.Loader,
) {
    init {
        events.register(ReloadEvent) { event ->
            val start = TimeSource.Monotonic.markNow()
            log.info("[DataLoader] Reloading bingo data...")
            val newData = ScopedData()

            val objectivesFuture = CompletableFuture.supplyAsync({
                newData.objectives = objectiveLoader.loadObjectives(event.resourceManager)
            }, event.prepareExecutor)
            val tierListsFuture = CompletableFuture.supplyAsync({
                newData.tierLists = tierListLoader.loadTierLists(event.resourceManager)
            }, event.prepareExecutor)
            val tagsFuture = CompletableFuture.supplyAsync({
                newData.tags = tagLoader.loadTags(event.resourceManager)
            }, event.prepareExecutor)
            val obtainableItemsFuture = CompletableFuture.supplyAsync({
                newData.obtainableItems = obtainableItemsLoader.loadObtainableItems(event.resourceManager)
            }, event.prepareExecutor)
            val teamPresetsFuture = CompletableFuture.supplyAsync({
                newData.teamPresets = teamPresetLoader.loadTeamPresets(event.resourceManager)
            }, event.prepareExecutor)
            val filterPresetsFuture = CompletableFuture.supplyAsync({
                newData.filterPresets = filterPresetsLoader.loadFilterPresets(event.resourceManager)
            }, event.prepareExecutor)
            val spawnKitsFuture = CompletableFuture.supplyAsync({
                newData.spawnKits = spawnKitLoader.loadSpawnKits(event.resourceManager)
            }, event.prepareExecutor)
            val commandsFuture = CompletableFuture.supplyAsync({
                newData.commandFiles = datapackFunctionLoader.loadCommands()
            }, event.prepareExecutor)
            val messagesFuture = CompletableFuture.supplyAsync({
                newData.messages = messageLoader.loadMessages(event.resourceManager)
            }, event.prepareExecutor)

            CompletableFuture.allOf(
                objectivesFuture,
                tierListsFuture,
                tagsFuture,
                obtainableItemsFuture,
                teamPresetsFuture,
                filterPresetsFuture,
                spawnKitsFuture,
                commandsFuture,
                messagesFuture,
            ).whenComplete { _, _ ->
                log.info("[DataLoader] Prepared in ${start.elapsedNow()}!")
            }.thenCompose(event.whenPrepared).thenAcceptAsync({
                scopedData.copyFrom(newData)
                log.info("[DataLoader] Completed in ${start.elapsedNow()}!")
            }, event.applyExecutor)
        }
    }
}