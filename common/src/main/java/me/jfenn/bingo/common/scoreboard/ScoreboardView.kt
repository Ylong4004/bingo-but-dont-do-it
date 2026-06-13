package me.jfenn.bingo.common.scoreboard

import me.jfenn.bingo.platform.scoreboard.ScoreChange
import net.minecraft.text.Text
import java.util.*

class ScoreboardView(
    val objectiveName: String,
) {

    var players = setOf<UUID>()

    private var contents: Map<String, Int> = emptyMap()

    class ScoreboardItem(
        val string: String,
        val text: Text,
    )

    fun createChanges(list: List<ScoreboardItem>): List<ScoreChange>
        = list.mapIndexed { i, item -> ScoreChange.Create(item.string, item.text, list.size - i) }

    fun updateContents(newList: List<ScoreboardItem>): List<ScoreChange> {
        val changes = mutableListOf<ScoreChange>()

        for ((i, newText) in newList.withIndex()) {
            val newIndex = newList.size - i
            val prevIndex = contents[newText.string]
            if (prevIndex == newIndex) continue

            if (prevIndex != null) {
                changes.add(ScoreChange.Update(newText.string, newText.text, newIndex))
            } else {
                changes.add(ScoreChange.Create(newText.string, newText.text, newIndex))
            }
        }

        for ((oldString, i) in contents) {
            if (newList.none { it.string == oldString }) {
                changes.add(ScoreChange.Remove(oldString, null, i))
            }
        }

        contents = newList.withIndex()
            .associate { (i, text) ->
                text.string to (newList.size - i)
            }

        return changes
    }

}