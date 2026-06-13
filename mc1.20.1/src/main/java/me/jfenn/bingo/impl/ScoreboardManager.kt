package me.jfenn.bingo.impl

import me.jfenn.bingo.platform.text.IText
import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.scoreboard.IObjectiveHandle
import me.jfenn.bingo.platform.scoreboard.IScoreboardManager
import me.jfenn.bingo.platform.scoreboard.ScoreChange
import net.minecraft.network.packet.s2c.play.ScoreboardDisplayS2CPacket
import net.minecraft.network.packet.s2c.play.ScoreboardObjectiveUpdateS2CPacket
import net.minecraft.network.packet.s2c.play.ScoreboardPlayerUpdateS2CPacket
import net.minecraft.scoreboard.ScoreboardCriterion
import net.minecraft.scoreboard.ScoreboardObjective
import net.minecraft.scoreboard.ServerScoreboard
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text

class ScoreboardManager(
    private val server: MinecraftServer,
) : IScoreboardManager {

    override fun createDummyObjective(name: String): IObjectiveHandle {
        val scoreboard: ServerScoreboard = server.scoreboard
        val objective = scoreboard.getNullableObjective(name) ?: run {
            scoreboard.addObjective(
                name,
                ScoreboardCriterion.DUMMY,
                Text.empty(),
                ScoreboardCriterion.RenderType.INTEGER,
            )
        }

        return ObjectiveHandle(scoreboard, objective)
    }

    override fun removeObjective(handle: IObjectiveHandle) {
        require(handle is ObjectiveHandle)
        val scoreboard: ServerScoreboard = server.scoreboard
        scoreboard.removeObjective(handle.objective)
    }

    override fun setScoreboardText(handle: IObjectiveHandle, textLines: List<ScoreChange.Create>) {
        require(handle is ObjectiveHandle)

        // Remove any previous sidebar lines that have been changed
        for (score in server.scoreboard.getAllPlayerScores(handle.objective)) {
            if (textLines.none { it.name == score.playerName }) {
                server.scoreboard.resetPlayerScore(score.playerName, handle.objective)
            }
        }

        textLines.forEachIndexed { i, text ->
            val score = server.scoreboard.getPlayerScore(text.name, handle.objective)
            score.score = textLines.size - i - 1
        }
    }

    override fun sendObjectiveCreate(player: IPlayerHandle, handle: IObjectiveHandle) {
        require(handle is ObjectiveHandle)
        val serverPlayer: ServerPlayerEntity = player.player

        serverPlayer.networkHandler.sendPacket(
            ScoreboardObjectiveUpdateS2CPacket(
                handle.objective,
                ScoreboardObjectiveUpdateS2CPacket.ADD_MODE,
            )
        )

        serverPlayer.networkHandler.sendPacket(
            ScoreboardDisplayS2CPacket(1, handle.objective)
        )
    }

    override fun sendObjectiveDelete(player: IPlayerHandle, handle: IObjectiveHandle) {
        require(handle is ObjectiveHandle)
        val serverPlayer: ServerPlayerEntity = player.player

        serverPlayer.networkHandler.sendPacket(
            ScoreboardObjectiveUpdateS2CPacket(
                handle.objective,
                ScoreboardObjectiveUpdateS2CPacket.REMOVE_MODE,
            )
        )
    }

    override fun sendObjectiveDisplayUpdate(player: IPlayerHandle, handle: IObjectiveHandle) {
        require(handle is ObjectiveHandle)
        val serverPlayer: ServerPlayerEntity = player.player

        serverPlayer.networkHandler.sendPacket(
            ScoreboardObjectiveUpdateS2CPacket(
                handle.objective,
                ScoreboardObjectiveUpdateS2CPacket.UPDATE_MODE,
            )
        )
    }

    override fun sendScoreChanges(player: IPlayerHandle, handle: IObjectiveHandle, changes: List<ScoreChange>) {
        require(handle is ObjectiveHandle)
        val serverPlayer: ServerPlayerEntity = player.player

        for (change in changes) {
            when (change) {
                is ScoreChange.Create -> {
                    serverPlayer.networkHandler.sendPacket(ScoreboardPlayerUpdateS2CPacket(ServerScoreboard.UpdateMode.CHANGE, handle.name, change.name, change.value))
                }
                is ScoreChange.Update -> {
                    serverPlayer.networkHandler.sendPacket(ScoreboardPlayerUpdateS2CPacket(ServerScoreboard.UpdateMode.CHANGE, handle.name, change.name, change.value))
                }
                is ScoreChange.Remove -> {
                    serverPlayer.networkHandler.sendPacket(ScoreboardPlayerUpdateS2CPacket(ServerScoreboard.UpdateMode.REMOVE, handle.name, change.name, change.value))
                }
            }
        }
    }

    override fun getPlayerName(player: IPlayerHandle): String {
        val serverPlayer: ServerPlayerEntity = player.player
        return serverPlayer.entityName
    }

    override fun getByName(name: String): IObjectiveHandle? {
        val scoreboard: ServerScoreboard = server.scoreboard
        val objective = scoreboard.getNullableObjective(name) ?: return null
        return ObjectiveHandle(scoreboard, objective)
    }
}

class ObjectiveHandle(
    private val scoreboard: ServerScoreboard,
    val objective: ScoreboardObjective,
) : IObjectiveHandle {
    override val name: String
        get() = objective.name

    override var displayName: IText
        get() = TextImpl(objective.displayName.copy())
        set(value) { objective.displayName = value.value }

    override fun getForPlayer(player: IPlayerHandle): Int? {
        val playerImpl = player as PlayerHandle
        return scoreboard.getPlayerScore(playerImpl.player.entityName, objective)?.score
    }

    override fun setPlayer(player: IPlayerHandle, value: Int) {
        val playerImpl = player as PlayerHandle
        scoreboard.getPlayerScore(playerImpl.player.entityName, objective).apply {
            score = value
        }
    }
}
