package me.jfenn.bingo.platform.scoreboard

import me.jfenn.bingo.platform.text.IText
import me.jfenn.bingo.platform.IPlayerHandle

interface IScoreboardManager {

    fun createDummyObjective(name: String): IObjectiveHandle

    fun removeObjective(handle: IObjectiveHandle)

    fun setScoreboardText(handle: IObjectiveHandle, textLines: List<ScoreChange.Create>)

    fun sendObjectiveCreate(player: IPlayerHandle, handle: IObjectiveHandle)

    fun sendObjectiveDelete(player: IPlayerHandle, handle: IObjectiveHandle)

    fun sendObjectiveDisplayUpdate(player: IPlayerHandle, handle: IObjectiveHandle)

    fun sendScoreChanges(player: IPlayerHandle, handle: IObjectiveHandle, changes: List<ScoreChange>)

    fun getPlayerName(player: IPlayerHandle): String

    fun getByName(name: String): IObjectiveHandle?

}

interface IObjectiveHandle {
    val name: String
    var displayName: IText
    fun getForPlayer(player: IPlayerHandle): Int?
    fun setPlayer(player: IPlayerHandle, value: Int)
}
