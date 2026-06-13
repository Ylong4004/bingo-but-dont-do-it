package me.jfenn.bingo.common.data

import me.jfenn.bingo.common.card.filter.ObjectiveFilterPresets
import me.jfenn.bingo.common.spawn.SpawnKits
import me.jfenn.bingo.common.state.GameState
import me.jfenn.bingo.common.team.BingoTeamKey
import me.jfenn.bingo.common.team.BingoTeamPreset
import me.jfenn.bingo.common.text.MessageData
import me.jfenn.bingo.common.text.MessageService.MessageType

internal class ScopedData {
    var teamPresets: Map<BingoTeamKey, BingoTeamPreset> = emptyMap()
    var objectives: ObjectiveType = emptyMap()
    var spawnKits: SpawnKits = SpawnKits()
    var tierLists: TierListType = emptyMap()
    var filterPresets: ObjectiveFilterPresets = ObjectiveFilterPresets.EMPTY
    var tags: TagType = emptyMap()
    var obtainableItems: Set<String> = emptySet()
    var commandFiles: Map<GameState, String> = emptyMap()
    var messages: Map<MessageType, MessageData> = emptyMap()

    fun copyFrom(other: ScopedData) {
        teamPresets = other.teamPresets
        objectives = other.objectives
        spawnKits = other.spawnKits
        tierLists = other.tierLists
        filterPresets = other.filterPresets
        tags = other.tags
        obtainableItems = other.obtainableItems
        commandFiles = other.commandFiles
        messages = other.messages
    }
}