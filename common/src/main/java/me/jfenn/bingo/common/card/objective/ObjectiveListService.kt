package me.jfenn.bingo.common.card.objective

import me.jfenn.bingo.common.utils.cacheFor
import me.jfenn.bingo.common.utils.seconds

class ObjectiveListService(
    private val objectiveManager: BingoObjectiveManager,
) {
    private val objectivesCache = cacheFor(5.seconds) { _: Unit -> objectiveManager.list() }

    fun getAllObjectives(): Iterable<String> = objectivesCache.get(Unit)
}