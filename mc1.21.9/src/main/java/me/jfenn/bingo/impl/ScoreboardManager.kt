package me.jfenn.bingo.impl

import me.jfenn.bingo.platform.text.IText
import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.scoreboard.IObjectiveHandle
import me.jfenn.bingo.platform.scoreboard.IScoreboardManager
import me.jfenn.bingo.platform.scoreboard.ScoreChange
import net.minecraft.network.packet.s2c.play.ScoreboardDisplayS2CPacket
import net.minecraft.network.packet.s2c.play.ScoreboardObjectiveUpdateS2CPacket
import net.minecraft.network.packet.s2c.play.ScoreboardScoreResetS2CPacket
import net.minecraft.network.packet.s2c.play.ScoreboardScoreUpdateS2CPacket
import net.minecraft.scoreboard.*
import net.minecraft.scoreboard.number.BlankNumberFormat
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import java.util.*

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
                false,
                BlankNumberFormat.INSTANCE,
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
        for (score in server.scoreboard.getScoreboardEntries(handle.objective)) {
            if (textLines.none { it.name == score.owner }) {
                server.scoreboard.removeScore({ score.owner }, handle.objective)
            }
        }

        textLines.forEachIndexed { i, text ->
            val score = server.scoreboard.getOrCreateScore(
                object : ScoreHolder {
                    override fun getNameForScoreboard(): String = text.name
                    override fun getDisplayName(): Text = text.text
                },
                handle.objective,
                true
            )
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
            ScoreboardDisplayS2CPacket(ScoreboardDisplaySlot.SIDEBAR, handle.objective)
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
                    serverPlayer.networkHandler.sendPacket(ScoreboardScoreUpdateS2CPacket(change.name, handle.name, change.value, Optional.of(change.text), Optional.empty()))
                }
                is ScoreChange.Update -> {
                    serverPlayer.networkHandler.sendPacket(ScoreboardScoreUpdateS2CPacket(change.name, handle.name, change.value, Optional.of(change.text), Optional.empty()))
                }
                is ScoreChange.Remove -> {
                    serverPlayer.networkHandler.sendPacket(ScoreboardScoreResetS2CPacket(change.name, handle.name))
                }
            }
        }
    }

    override fun getPlayerName(player: IPlayerHandle): String {
        val serverPlayer: ServerPlayerEntity = player.player
        return serverPlayer.nameForScoreboard
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
        return scoreboard.getScore(playerImpl.player, objective)?.score
    }

    override fun setPlayer(player: IPlayerHandle, value: Int) {
        val playerImpl = player as PlayerHandle
        scoreboard.getOrCreateScore(playerImpl.player, objective).apply {
            score = value
        }
    }
}
