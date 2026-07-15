package me.jfenn.bingo.integrations.ddi

import me.jfenn.bingo.mixinhandler.PlayerListNameDecorators
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.platform.IPlayerManager
import net.minecraft.scoreboard.ScoreboardCriterion
import net.minecraft.scoreboard.ScoreboardDisplaySlot
import net.minecraft.scoreboard.ScoreboardObjective
import net.minecraft.scoreboard.number.StyledNumberFormat
import net.minecraft.server.MinecraftServer
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import org.slf4j.Logger
import java.util.UUID

/** Publishes authoritative remaining DDI lives in the vanilla Tab player list. */
class DDITabLivesService(
    private val server: MinecraftServer,
    private val playerManager: IPlayerManager,
    private val text: TextProvider,
    private val log: Logger,
) {
    private var livesProvider: ((UUID) -> Int?)? = null
    private var objective: ScoreboardObjective? = null
    private var decoratorHandle: AutoCloseable? = null

    fun start(provider: (UUID) -> Int?) {
        stop()
        livesProvider = provider
        selectDisplayMode()
        refresh()
    }

    /** Refreshes scores and also notices if another mod has taken over the LIST slot. */
    fun refresh() {
        if (livesProvider == null) return
        val scoreboard = server.scoreboard
        val activeObjective = objective
        if (activeObjective != null) {
            if (scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.LIST) !== activeObjective) {
                switchToNameSuffix()
                return
            }
            for (player in server.playerManager.playerList) {
                val lives = livesProvider?.invoke(player.uuid)
                if (lives == null) {
                    scoreboard.removeScore(player, activeObjective)
                } else {
                    scoreboard.getOrCreateScore(player, activeObjective).score = lives.coerceAtLeast(0)
                }
            }
            return
        }

        if (scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.LIST) == null) {
            decoratorHandle?.close()
            decoratorHandle = null
            claimListObjective()
            refreshPlayerNames()
            refresh()
        } else {
            refreshPlayerNames()
        }
    }

    fun stop() {
        val scoreboard = server.scoreboard
        val activeObjective = objective
        if (activeObjective != null) {
            if (scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.LIST) === activeObjective) {
                scoreboard.setObjectiveSlot(ScoreboardDisplaySlot.LIST, null)
            }
            if (scoreboard.getNullableObjective(activeObjective.name) === activeObjective) {
                scoreboard.removeObjective(activeObjective)
            }
        }
        objective = null
        livesProvider = null
        decoratorHandle?.close()
        decoratorHandle = null
        refreshPlayerNames()
    }

    private fun selectDisplayMode() {
        val existing = server.scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.LIST)
        if (existing == null) {
            claimListObjective()
        } else {
            log.info(
                "[DDI] Player-list slot is owned by {}; appending lives to player names instead",
                existing.name,
            )
            switchToNameSuffix()
        }
    }

    private fun claimListObjective() {
        val scoreboard = server.scoreboard
        val objectiveName = generateSequence(0) { it + 1 }
            .map { index -> if (index == 0) OBJECTIVE_NAME else "bingo_ddi_lv$index" }
            .first { scoreboard.getNullableObjective(it) == null }
        val created = scoreboard.addObjective(
            objectiveName,
            ScoreboardCriterion.DUMMY,
            Text.literal("DDI ❤"),
            ScoreboardCriterion.RenderType.INTEGER,
            false,
            StyledNumberFormat.RED,
        )
        created.displayName = Text.literal("DDI ❤")
        created.renderType = ScoreboardCriterion.RenderType.INTEGER
        created.numberFormat = StyledNumberFormat.RED
        scoreboard.setObjectiveSlot(ScoreboardDisplaySlot.LIST, created)
        objective = created
        log.debug("[DDI] Showing remaining lives in the player-list score column")
    }

    private fun switchToNameSuffix() {
        objective?.let { oldObjective ->
            if (server.scoreboard.getNullableObjective(oldObjective.name) === oldObjective) {
                server.scoreboard.removeObjective(oldObjective)
            }
        }
        objective = null
        if (decoratorHandle == null) {
            decoratorHandle = PlayerListNameDecorators.register { uuid, current ->
                val lives = livesProvider?.invoke(uuid) ?: return@register null
                text.empty()
                    .append(current)
                    .append("  ")
                    .append(
                        text.literal("${lives.coerceAtLeast(0)}♥").formatted(
                            if (lives > 0) Formatting.RED else Formatting.DARK_GRAY
                        )
                    )
            }
        }
        refreshPlayerNames()
    }

    private fun refreshPlayerNames() {
        playerManager.getPlayers().forEach(playerManager::updatePlayerListName)
    }

    private companion object {
        const val OBJECTIVE_NAME = "bingo_ddi_lives"
    }
}
