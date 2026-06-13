package me.jfenn.bingo.common.stats

import me.jfenn.bingo.common.LOBBY_WORLD_ID
import me.jfenn.bingo.common.data.ScopedData
import me.jfenn.bingo.common.event.ScopedEvents
import me.jfenn.bingo.common.lobbyWorld
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.state.GameState
import me.jfenn.bingo.common.stats.data.PlayerStatsSummary
import me.jfenn.bingo.common.team.BingoTeam
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.common.utils.formatString
import me.jfenn.bingo.common.utils.minus
import me.jfenn.bingo.common.utils.seconds
import me.jfenn.bingo.generated.StringKey
import me.jfenn.bingo.platform.*
import me.jfenn.bingo.platform.item.IItemStackFactory
import me.jfenn.bingo.platform.text.IText
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Formatting
import org.joml.Matrix4f
import org.joml.Vector3d
import org.joml.Vector3f
import org.joml.Vector4f
import org.slf4j.Logger
import java.time.Instant
import java.util.*
import java.util.concurrent.CompletableFuture

internal class StatsLobbyController(
    private val log: Logger,
    private val server: MinecraftServer,
    private val state: BingoState,
    private val data: ScopedData,
    private val stats: StatsService,
    private val text: TextProvider,
    private val playerManager: IPlayerManager,
    private val itemStackFactory: IItemStackFactory,
    private val entityManager: IEntityManager,
    events: ScopedEvents
) {
    companion object {
        private val DURATION_DELAY = 5.seconds
        private val DURATION_RESET = 10.seconds
    }

    private var showingPlayer: UUID? = null
    private var showingStartedAt: Instant = Instant.MIN

    private var pendingStats: CompletableFuture<PlayerStatsSummary>? = null

    private var statsTransform: Matrix4f? = null

    private fun IEntity.transformPos(matrix: Matrix4f, pos: Vector3d) {
        val pos4f = with(pos) { Vector4f(x.toFloat(), y.toFloat(), z.toFloat(), 1f) }
        this.pos = matrix.transform(pos4f).let { Vector3d(it.x.toDouble(), it.y.toDouble(), it.z.toDouble()) }

        val angles = Vector3f()
        matrix.getEulerAnglesXYZ(angles)
        yaw += Math.toDegrees(-angles.y.toDouble()).toFloat()
    }

    private var nameEntityUuid: UUID? = null
    private val nameEntity: ITextDisplayEntity? get() {
        val lobbyWorld = server.lobbyWorld ?: return null

        nameEntityUuid
            ?.let { entityManager.getEntity(lobbyWorld, it) as? ITextDisplayEntity }
            ?.let { return it }

        val transform = statsTransform ?: return null

        val entity = entityManager.createEntity(EntityType.TEXT_DISPLAY, lobbyWorld)
            .apply {
                transformPos(transform, Vector3d(0.0, 2.2, 0.0))

                value = text.empty()
                billboard = ITextDisplayEntity.Billboard.FIXED
                alignment = ITextDisplayEntity.TextAlignment.RIGHT
                background = 0x80_000000.toInt()
            }
            .also {
                entityManager.spawnEntity(lobbyWorld, it)
            }

        nameEntityUuid = entity.uuid
        return entity
    }

    private var leftEntityUuid: UUID? = null
    private val leftEntity: ITextDisplayEntity? get() {
        val lobbyWorld = server.lobbyWorld ?: return null

        leftEntityUuid
            ?.let { entityManager.getEntity(lobbyWorld, it) as? ITextDisplayEntity }
            ?.let { return it }

        val transform = statsTransform ?: return null

        val entity = entityManager.createEntity(EntityType.TEXT_DISPLAY, lobbyWorld)
            .apply {
                transformPos(transform, Vector3d(-1.8, 0.5, 0.0))

                value = text.empty()
                billboard = ITextDisplayEntity.Billboard.FIXED
                alignment = ITextDisplayEntity.TextAlignment.RIGHT
                background = 0xb0_000000.toInt()
                transformation = Matrix4f().scale(.5f)
            }
            .also {
                entityManager.spawnEntity(lobbyWorld, it)
            }

        leftEntityUuid = entity.uuid
        return entity
    }

    private var rightEntityUuid: UUID? = null
    private val rightEntity: ITextDisplayEntity? get() {
        val lobbyWorld = server.lobbyWorld ?: return null

        rightEntityUuid
            ?.let { entityManager.getEntity(lobbyWorld, it) as? ITextDisplayEntity }
            ?.let { return it }

        val transform = statsTransform ?: return null

        val entity = entityManager.createEntity(EntityType.TEXT_DISPLAY, lobbyWorld)
            .apply {
                transformPos(transform, Vector3d(1.8, 0.5, 0.0))

                value = text.empty()
                billboard = ITextDisplayEntity.Billboard.FIXED
                alignment = ITextDisplayEntity.TextAlignment.LEFT
                background = 0xb0_000000.toInt()
                transformation = Matrix4f().scale(.5f)
            }
            .also {
                entityManager.spawnEntity(lobbyWorld, it)
            }

        rightEntityUuid = entity.uuid
        return entity
    }

    private fun getArmorStand() = server.lobbyWorld
        ?.let { entityManager.iterateEntities(it) }
        ?.mapNotNull { it as? IArmorStandEntity }
        ?.firstOrNull()

    private fun spawnSummary(
        player: ServerPlayerEntity,
        summary: PlayerStatsSummary,
    ) {
        val armorStand = getArmorStand()
        if (armorStand == null) {
            log.warn("[StatsLobbyController] Could not display player stats; no armor stand found")
            return
        }

        statsTransform = Matrix4f()
            .translate(armorStand.pos.let { Vector3f(it.x.toFloat(), it.y.toFloat(), it.z.toFloat()) })
            .rotateY(Math.toRadians(-armorStand.yaw.toDouble()).toFloat())

        val playerHead = itemStackFactory.createPlayerHead()
        playerHead.setSkullOwner(player)
        armorStand.equipStack(IArmorStandEntity.EquipmentSlot.HEAD, playerHead)

        nameEntity?.value = text.literal(playerManager.forPlayer(player).playerName)

        val favoriteTeam = summary.favoriteTeam
            ?.let { data.teamPresets[it] }
            ?.let { BingoTeam.fromPreset(summary.favoriteTeam, it) }
            ?.let { team ->
                text.empty()
                    .append(
                        team.getName(
                            textProvider = text,
                            symbol = true,
                            bracketed = false,
                            teamNameKey = null,
                        )
                    )
                    .append(" (${(summary.favoriteTeamPercentage * 100f).toInt()}%)")
            }

        val divider = text.literal("\uD83E\uDF2D".repeat(28))
        val allTimeStats = buildList<IText> {
            add(text.string(StringKey.StatsAllTime).append(" ★").formatted(Formatting.BOLD, Formatting.YELLOW))
            add(divider)
            add(text.empty())

            text.empty()
                .append(text.string(StringKey.StatsGames).formatted(Formatting.BOLD, Formatting.GRAY))
                .append(": ${summary.totalGames.formatLargeNumber()}")
                .also { add(it) }

            text.empty()
                .append(text.string(StringKey.StatsWinLoss).formatted(Formatting.BOLD, Formatting.GRAY))
                .append(": ${summary.totalWins.formatLargeNumber()}/${summary.totalLosses.formatLargeNumber()}")
                .also { add(it) }

            text.empty()
                .append(text.string(StringKey.StatsItems).formatted(Formatting.BOLD, Formatting.GRAY))
                .append(": ${summary.totalItems.formatLargeNumber()}")
                .also { add(it) }

            text.empty()
                .append(text.string(StringKey.StatsPlaytime).formatted(Formatting.BOLD, Formatting.GRAY))
                .append(": ${summary.totalPlaytime.formatString()}")
                .also { add(it) }

            add(text.empty())

            text.empty()
                .append(text.string(StringKey.StatsFavoriteTeam).formatted(Formatting.BOLD, Formatting.GRAY))
                .append(":")
                .also { add(it) }

            add(favoriteTeam ?: text.literal("-"))
        }.toMutableList()

        val monthlyStats = buildList<IText> {
            add(text.literal("\uD83D\uDCC6 ").append(text.string(StringKey.StatsMonthly)).formatted(Formatting.BOLD, Formatting.AQUA))
            add(divider)
            add(text.empty())

            text.empty()
                .append(text.string(StringKey.StatsGames).formatted(Formatting.BOLD, Formatting.GRAY))
                .append(": ${summary.monthlyGames.formatLargeNumber()}")
                .also { add(it) }

            text.empty()
                .append(text.string(StringKey.StatsWinLoss).formatted(Formatting.BOLD, Formatting.GRAY))
                .append(": ${summary.monthlyWins.formatLargeNumber()}/${summary.monthlyLosses.formatLargeNumber()}")
                .also { add(it) }

            text.empty()
                .append(text.string(StringKey.StatsItems).formatted(Formatting.BOLD, Formatting.GRAY))
                .append(": ${summary.monthlyItems.formatLargeNumber()}")
                .also { add(it) }

            text.empty()
                .append(text.string(StringKey.StatsPlaytime).formatted(Formatting.BOLD, Formatting.GRAY))
                .append(": ${summary.monthlyPlaytime.formatString()}")
                .also { add(it) }

            add(text.empty())

            text.empty()
                .append(text.string(StringKey.StatsWinStreak).formatted(Formatting.BOLD, Formatting.GRAY))
                .append(": ")
                .append(
                    when {
                        summary.winStreakBest != null -> {
                            text.string(StringKey.StatsWinStreakValue, summary.winStreak, summary.winStreakBest)
                        }
                        else -> text.literal(summary.winStreak.toString())
                    }
                )
                .also { add(it) }
        }.toMutableList()

        // ensure that both lists have an equal height
        while (allTimeStats.size < monthlyStats.size)
            allTimeStats.add(text.empty())

        while (monthlyStats.size < allTimeStats.size)
            monthlyStats.add(text.empty())

        val margin = " ".repeat(4)
        text.literal("\n")
            .also { text ->
                for (t in allTimeStats)
                    text.append(margin).append(t).append("$margin\n")
            }
            .let { leftEntity?.value = it }

        text.literal("\n")
            .also { text ->
                for (t in monthlyStats)
                    text.append(margin).append(t).append("$margin\n")
            }
            .let { rightEntity?.value = it }
    }

    private fun resetSummary() {
        leftEntity?.value = text.empty()
        rightEntity?.value = text.empty()
        nameEntity?.value = text.empty()
        getArmorStand()?.equipStack(IArmorStandEntity.EquipmentSlot.HEAD, itemStackFactory.emptyStack)
    }

    private fun tickPlayer(player: IPlayerHandle) {
        val now = Instant.now()
        // if the stats are already being shown for this player, return
        if (showingPlayer == player.uuid) {
            showingStartedAt = now
            return
        }

        // if the last change was less than DURATION_DELAY ago... then don't let another player change it yet
        if (now - showingStartedAt < DURATION_DELAY) {
            return
        }

        log.info("[StatsLobbyController] Fetching summary for player ${player.uuid}...")
        showingPlayer = player.uuid
        showingStartedAt = now
        pendingStats = stats.getPlayerSummaryAsync(player.uuid)
    }

    private fun tickPlayers() {
        val player = playerManager.getPlayers()
            .filter { it.world.identifier == LOBBY_WORLD_ID.toString() }
            .find { player ->
                player.world.getBlockState(player.blockPos).identifier == "minecraft:heavy_weighted_pressure_plate"
            }

        if (player != null) {
            tickPlayer(player)
            return
        }

        // if no player is activating the stats and DURATION_RESET has passed...
        if (Instant.now() - showingStartedAt > DURATION_RESET && showingPlayer != null) {
            showingPlayer = null
            resetSummary()
        }
    }

    private fun tickStats() {
        val pending = pendingStats ?: return
        if (!pending.isDone) return
        pendingStats = null

        val result = pending.getNow(null) ?: return
        val player = showingPlayer
            ?.let { server.playerManager.getPlayer(it) }
            ?: return

        log.info("[StatsLobbyController] Finished fetching player summary!")
        spawnSummary(player, result)
    }

    init {
        events.onGameTick {
            if (state.state != GameState.PREGAME) return@onGameTick

            tickPlayers()
            tickStats()
        }
    }
}