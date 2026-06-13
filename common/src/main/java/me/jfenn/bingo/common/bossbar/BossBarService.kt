package me.jfenn.bingo.common.bossbar

import me.jfenn.bingo.common.MOD_ID_BINGO
import me.jfenn.bingo.common.config.PlayerSettingsService
import me.jfenn.bingo.common.scope.BingoComponent
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.team.BingoTeam
import me.jfenn.bingo.common.team.BingoTeamKey
import me.jfenn.bingo.common.team.TeamService
import me.jfenn.bingo.platform.IBossBar
import me.jfenn.bingo.platform.IBossBarManager
import me.jfenn.bingo.platform.IPlayerHandle
import net.minecraft.text.Text

internal class BossBarService(
    private val state: BingoState,
    private val playerSettingsService: PlayerSettingsService,
    private val teamService: TeamService,
    private val bossBarManager: IBossBarManager
) : BingoComponent() {

    private fun createBossbar(team: BingoTeam?): IBossBar {
        val id = "$MOD_ID_BINGO:${team?.id ?: "preview"}"
        val bar = bossBarManager.get(id)
            ?: bossBarManager.add(id, Text.literal("Time Remaining"))

        return bar.apply {
            color = IBossBar.Color.WHITE
            style = IBossBar.Style.PROGRESS
            value = 0
            maxValue = 10_000
        }
    }

    private val bossBars = mutableMapOf<BingoTeamKey?, IBossBar>()

    fun getBossbar(team: BingoTeam?) = bossBars.getOrPut(team?.key) { createBossbar(team) }

    fun updateBossBar(player: IPlayerHandle) {
        val settings = playerSettingsService.getPlayer(player)
        val team = teamService.getPlayerTeam(player)

        for ((teamKey, bossBar) in bossBars) {
            val shouldIncludePlayer = state.isLobbyMode || team != null
            if (settings.bossbar && teamKey == team?.key && shouldIncludePlayer)
                bossBar.addPlayer(player)
            else bossBar.removePlayer(player)
        }
    }

    fun clearBossBars() {
        for (bossBar in bossBars.values) {
            bossBarManager.remove(bossBar)
        }
        bossBars.clear()

        // Clear any remaining bingo bossbars (broken state) and server bossbars (dragon/wither/raid)
        bossBarManager.list()
            .filter { it.id?.startsWith("$MOD_ID_BINGO:") == true || it.id == null }
            .forEach { bossBarManager.remove(it) }
    }

}