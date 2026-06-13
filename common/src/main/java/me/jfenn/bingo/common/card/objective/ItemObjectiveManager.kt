package me.jfenn.bingo.common.card.objective

import me.jfenn.bingo.common.NBT_BINGO_IGNORE
import me.jfenn.bingo.common.card.BingoCard
import me.jfenn.bingo.common.card.CardGeneratorState
import me.jfenn.bingo.common.card.data.ObjectiveData
import me.jfenn.bingo.common.card.data.rootObjectives
import me.jfenn.bingo.common.card.objective.ObjectiveDisplay.Companion.FORMAT_COUNT
import me.jfenn.bingo.common.card.objective.ObjectiveDisplay.Companion.FORMAT_MIN
import me.jfenn.bingo.common.data.ScopedData
import me.jfenn.bingo.common.map.CardTile
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.item.IItemStack
import me.jfenn.bingo.platform.item.IItemStackFactory
import net.minecraft.server.MinecraftServer
import org.slf4j.Logger
import java.time.Instant
import kotlin.math.max

internal class ItemObjectiveManager(
    private val log: Logger,
    private val server: MinecraftServer,
    private val state: BingoState,
    private val itemStackFactory: IItemStackFactory,
    private val data: ScopedData,
    private val objectiveDisplayService: ObjectiveDisplayService,
    private val objectiveService: ObjectiveService,
): IObjectiveManager {
    override fun list(): Iterable<String> {
        val dataKeys = data.objectives.rootObjectives
            .filter { (_, it) -> it is ObjectiveData.Item }
            .map { it.key }
        return itemStackFactory.listItems(server) + dataKeys
    }

    override fun listExcludedIds(): Iterable<String> {
        return objectiveService.getAllTeamPlayers()
            .flatMap { (_, player) -> player.allHeldStacks() }
            .map { it.identifier.toString() }
            .asIterable()
    }

    override fun find(id: String, state: CardGeneratorState): BingoObjective.ItemEntry? {
        // If the objective is a data ID
        (data.objectives[id] as? ObjectiveData.Item)
            ?.let { data ->
                BingoObjective.ItemEntry(
                    id = id,
                    data = data,
                    itemCount = data.count.resolve(state),
                )
            }
            ?.takeIf { init(it, isDataObjective = true) }
            ?.let { return it }

        // If the objective is a direct item identifier
        return BingoObjective.ItemEntry(
            id = id,
            data = ObjectiveData.Item(item = id),
        ).takeIf { init(it) }
    }

    private fun init(objective: BingoObjective, isDataObjective: Boolean = false): Boolean {
        if (objective !is BingoObjective.ItemEntry) return false

        val stack = try {
            if (!itemStackFactory.isEnabledInWorld(objective.itemId, server))
                throw IllegalArgumentException()

            itemStackFactory.createStack(objective.itemId, count = objective.itemCount)
        } catch (e: Throwable) {
            if (isDataObjective) {
                log.objectiveError(objective.id, "Item ${objective.itemId} could not be found.")
            }
            return false
        }

        val isNbtSuccessful = stack.setNbtString(objective.itemNbt)
        val isComponentsSuccessful = objective.itemComponents?.let { stack.setComponentsString(it) } ?: true

        if (!isNbtSuccessful || !isComponentsSuccessful) {
            log.objectiveError(objective.id, "Item ${objective.itemId} NBT failed to parse")
            return false
        }

        objective.itemStack = stack
        objective.itemCount = objective.itemCount.coerceIn(1, stack.maxCount)

        val display = ObjectiveDisplay.Resolved(
            name = stack.displayName.copy()
                .append(if (objective.itemCount > 1) " x${FORMAT_COUNT}" else ""),
            item = stack,
            decoration = when {
                objective.itemNbt != null || !objective.itemComponents.isNullOrEmpty() -> CardTile.Decoration.ADVANCEMENT
                else -> CardTile.Decoration.NONE
            }
        )
        objective.display = objectiveDisplayService.resolve(
            id = objective.id,
            data = objective.data.display,
            fallback = display,
            substitutions = mapOf(
                FORMAT_COUNT to objective.itemCount.toString(),
                FORMAT_MIN to objective.itemCount.toString(),
            )
        )

        return true
    }

    override fun init(card: BingoCard) {
        for (goal in card.objectivesByInstance<BingoObjective.ItemEntry>()) {
            if (!init(goal)) {
                log.objectiveError(goal.id, "Unable to find item '${goal.itemId}'")
            }
        }
    }

    private fun canSatisfyPartial(stack: IItemStack, objective: BingoObjective.ItemEntry) : Boolean {
        val objectiveStack = objective.itemStack ?: return false
        return stack.item == objectiveStack.item &&
                !stack.hasCustomTag(NBT_BINGO_IGNORE) &&
                stack.isDataOverlapping(nbt = objective.itemNbt, components = objective.itemComponents)
    }

    private fun countSatisfied(stacks: List<IItemStack>, objective: BingoObjective.ItemEntry) : Int {
        val count = stacks
            .filter { canSatisfyPartial(it, objective) }
            .sumOf { it.count }

        return count
    }

    fun confiscateScoredItem(
        player: IPlayerHandle,
        objective: BingoObjective.ItemEntry,
    ) {
        val itemViews = player.allHeldStackViews()
            .filter { canSatisfyPartial(it.stack, objective) }

        // Confiscate item stacks until the objective requirements are met
        var countRemaining = objective.itemCount
        for (view in itemViews) {
            val newCount = view.stack.count - countRemaining
            if (newCount < 0) {
                // the entire stack is consumed, and there are remaining items to confiscate
                countRemaining -= view.stack.count
                view.mutate { it.count = 0 }
            } else {
                // there are no remaining items to confiscate
                view.mutate { it.count = newCount }
                break
            }
        }
    }


    override fun tick(card: BingoCard) {
        val now = state.updatedAt ?: Instant.MIN

        val playersToItems = objectiveService.getTeamPlayers(card)
            .map { (team, player) ->
                val inventory = player.allHeldStacks().toList()
                Triple(team, player, inventory)
            }

        for (objective in card.objectivesByInstance<BingoObjective.ItemEntry>()) {
            objectiveService.updateTeamsOnceAchieved(objective)

            // team progress is recalculated on each tick...
            objective.teamsProgress.clear()
        }

        // first, we need to check that each player still has all their items
        // and remove them from playersHolding if not
        for (objective in card.objectivesByInstance<BingoObjective.ItemEntry>()) {
            // If the objective is permanent, the objective should remain captured
            if (objective.data.permanent)
                continue

            for ((_, player, inventory) in playersToItems) {
                // if the player has no objectives to lose, skip
                if (!objective.playersHolding.containsKey(player.uuid)) continue

                val count = countSatisfied(inventory, objective)

                // if the player does not have the item anymore, mark it lost
                if (count < objective.itemCount) {
                    objective.playersHolding.remove(player.uuid)
                }
            }
        }

        for ((team, player, inventory) in playersToItems) {
            // check inventory for new collected items
            val objectives = inventory.asSequence()
                .map { it.identifier.toString() }
                .distinct()
                .flatMap { card.itemObjectives[it] ?: emptyList() }

            for (objective in objectives) {
                // count the number of items in the player's inventory that can satisfy this objective
                val count = countSatisfied(inventory, objective)

                // if the item hasn't been achieved and can be satisfied, mark it as collected
                if (count > 0) {
                    objective.teamsSeen += team.key
                } else {
                    continue
                }

                val progress = count / objective.itemCount.coerceAtLeast(1).toFloat()
                objective.teamsProgress.compute(team.key) { _, prev -> max(prev ?: 0f, progress) }

                if (count < objective.itemCount)
                    continue

                if (!objective.playersHolding.containsKey(player.uuid)) {
                    objective.playersHolding[player.uuid] = BingoObjectiveCapture(
                        team = team.key,
                        player = player.profile,
                        instant = now,
                    )
                }
            }
        }

        for (objective in card.objectivesByInstance<BingoObjective.ItemEntry>()) {
            val players = objectiveService.getPlayers(
                card = card,
                playersHolding = objective.playersHolding,
                players = objective.players,
            ).toMutableMap()
            val teams = objectiveService.getTeams(
                players = players
            ).toMutableSet()

            objectiveService.update(
                objective = objective,
                players = players,
                teams = teams,
            )
        }
    }
}