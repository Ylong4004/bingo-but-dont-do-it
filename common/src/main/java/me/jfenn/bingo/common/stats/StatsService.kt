package me.jfenn.bingo.common.stats

import me.jfenn.bingo.common.config.BingoConfig
import me.jfenn.bingo.common.options.BingoOptions
import me.jfenn.bingo.common.stats.data.*
import me.jfenn.bingo.common.team.BingoTeamKey
import me.jfenn.bingo.platform.IExecutors
import me.jfenn.bingo.sql.BingoDatabase
import org.slf4j.Logger
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class StatsService(
    private val log: Logger,
    private val db: BingoDatabase,
    executors: IExecutors,
    config: BingoConfig,
) {

    private val executor = executors.io
    private val hostId = config.statsHostId

    fun checkIfIdsExist(playerUuid: UUID, gameIds: Set<UUID>): Set<UUID> {
        val existingIds = db.gameQueries.checkIfIdsExist(
            id = gameIds.map { it.toString() },
            minecraft_id = playerUuid.toString(),
        ).executeAsList()
            .map { UUID.fromString(it) }
            .toSet()

        return gameIds - existingIds
    }

    fun findById(gameId: UUID, playerId: UUID): PlayerGameSummary? {
        val game = db.gameQueries.findById(gameId.toString())
            .executeAsOneOrNull()
            ?: return null
        val player = db.gamePlayerQueries.findById(minecraft_id = playerId.toString(), game_id = gameId.toString())
            .executeAsOneOrNull()
            ?: return null
        val team = db.gameTeamQueries.findById(id = player.team_id, game_id = gameId.toString())
            .executeAsOneOrNull()
            ?: return null

        return PlayerGameSummary(
            game = GameInfo(game),
            team = GameTeamInfo(team),
            player = GamePlayerInfo(player),
        )
    }

    fun insertGame(
        game: GameInfo,
        teams: List<GameTeamInfo>,
        players: List<GamePlayerInfo>
    ) {
        db.transaction {
            db.gameQueries.upsert(game.toGame())
            for (team in teams) {
                db.gameTeamQueries.upsert(team.toGameTeam())
            }
            for (player in players) {
                db.gamePlayerQueries.upsert(player.toGamePlayer())
            }
        }
    }

    fun updateBestStats(
        game: GameInfo,
        players: List<GamePlayerInfo>
    ) {
        if (!game.isDraw && !game.isForfeit) {
            kotlin.runCatching {
                db.bestStatsQueries.updateTime(
                    hash = game.bingoOptionsHash,
                    host_id = hostId.toString(),
                    is_singleplayer = if (game.playerCount == 1) 1L else 0L,
                    game_id = game.id.toString(),
                    duration = game.duration.toMillis()
                )
            }.onFailure {
                log.error("Failure running updateTime", it)
            }
        }

        for (player in players) {
            kotlin.runCatching {
                db.bestStatsQueries.updateCapturedItems(
                    player_id = player.minecraftId.toString(),
                    hash = game.bingoOptionsHash,
                    host_id = hostId.toString(),
                    game_id = game.id.toString(),
                    items = player.capturedItems.toLong(),
                )
            }.onFailure {
                log.error("Failure running updateCapturedItems for {}", player.minecraftId, it)
            }

            kotlin.runCatching {
                val winStreak = getPlayerWinStreak(player.minecraftId)
                if (winStreak > 0L) {
                    db.bestStatsQueries.updateWinStreak(
                        player_id = player.minecraftId.toString(),
                        host_id = hostId.toString(),
                        streak = winStreak,
                    )
                }
            }.onFailure {
                log.error("Failure running updateWinStreak for {}", player.minecraftId, it)
            }
        }
    }

    fun fetchGamesByPlayer(
        playerUuid: UUID,
        itemsPerPage: Long = 100L
    ) = sequence {
        for (offset in 0L until Long.MAX_VALUE step itemsPerPage) {
            val games = db.gameQueries.allGamesByPlayer(
                minecraft_id = playerUuid.toString(),
                limit = itemsPerPage,
                offset = offset
            ).executeAsList()

            if (games.isEmpty()) break

            yield(games.map { UUID.fromString(it) })
        }
    }

    /**
     * Generate an SHA-256 hash of all game IDs played by the player,
     * for syncing purposes.
     */
    fun fetchGamesByPlayerSha512(playerId: UUID): String {
        val md = MessageDigest.getInstance("SHA-512")
        for (games in fetchGamesByPlayer(playerId)) {
            for (game in games) {
                md.update(game.toString().toByteArray())
            }
        }
        val digest = md.digest()
        return HexFormat.of().formatHex(digest)
    }

    fun getPlayerWinStreak(playerUuid: UUID): Long {
        return db.gameQueries.latestWinStreakByPlayer(
            minecraft_id = playerUuid.toString(),
            host_id = hostId.toString(),
        )
            .executeAsOneOrNull()
            ?: 0L
    }

    fun getPlayerSummary(playerUuid: UUID, now: Instant = Instant.now()): PlayerStatsSummary {
        val total = db.gameQueries.totalStatsByPlayer(playerUuid.toString()).executeAsOne()
        val monthly = db.gameQueries.monthlyStatsByPlayer(playerUuid.toString(), now.toString()).executeAsOne()
        val favoriteTeam = db.gamePlayerQueries.findFavoriteTeam(playerUuid.toString()).executeAsOneOrNull()
        val currentStreak = getPlayerWinStreak(playerUuid)
        val bestStreak = getBestWinStreak(playerUuid)

        return PlayerStatsSummary(
            totalPlaytime = Duration.ofMillis(total.duration ?: 0),
            totalItems = total.items ?: 0L,
            totalGames = total.games,
            totalWins = total.wins,
            totalLosses = total.losses,
            monthlyPlaytime = Duration.ofMillis(monthly.duration ?: 0),
            monthlyItems = monthly.items ?: 0L,
            monthlyGames = monthly.games,
            monthlyWins = monthly.wins,
            monthlyLosses = monthly.losses,
            favoriteTeam = favoriteTeam?.id?.let { BingoTeamKey(it) },
            favoriteTeamPercentage = favoriteTeam?.let { it.count / it.total.toFloat() } ?: 0f,
            winStreak = currentStreak,
            winStreakBest = bestStreak,
        )
    }

    fun getPlayerSummaryAsync(playerId: UUID): CompletableFuture<PlayerStatsSummary> {
        return CompletableFuture.supplyAsync({ getPlayerSummary(playerId) }, executor)
            .orTimeout(10L, TimeUnit.SECONDS)
    }

    fun getBestTime(options: BingoOptions, isSingleplayer: Boolean): Duration? {
        val bestTimeQuery = if (isSingleplayer) {
            db.bestStatsQueries.bestTimeSingleplayer(
                bingo_options_hash = options.getShaHash(),
                host_id = hostId.toString(),
            )
        } else {
            db.bestStatsQueries.bestTimeMultiplayer(
                bingo_options_hash = options.getShaHash(),
                host_id = hostId.toString(),
            )
        }

        return bestTimeQuery.executeAsOneOrNull()?.duration?.let { Duration.ofMillis(it) }
    }

    fun getBestTimeAsync(options: BingoOptions, isSingleplayer: Boolean): CompletableFuture<Duration?> {
        return CompletableFuture.supplyAsync({ getBestTime(options, isSingleplayer) }, executor)
            .orTimeout(10L, TimeUnit.SECONDS)
    }

    fun getBestCapturedItems(playerId: UUID, options: BingoOptions): Int? {
        val capturedItems = db.bestStatsQueries.bestCapturedItems(
            player_id = playerId.toString(),
            bingo_options_hash = options.getShaHash(),
            host_id = hostId.toString(),
        ).executeAsOneOrNull()?.captured_items

        return capturedItems?.toInt()
    }

    fun getBestWinStreak(playerId: UUID): Long? {
        val winStreak = db.bestStatsQueries.bestWinStreak(
            player_id = playerId.toString(),
            host_id = hostId.toString(),
        ).executeAsOneOrNull()?.win_streak

        return winStreak
    }

    fun resetPlayerStats(playerId: UUID) {
        db.bestStatsQueries.resetByPlayer(
            player_id = playerId.toString(),
            host_id = hostId.toString(),
        )
    }

    fun resetGameStats(options: BingoOptions) {
        db.bestStatsQueries.resetByHash(
            hash = options.getShaHash(),
            host_id = hostId.toString(),
        )
    }

    fun resetAllStats() {
        db.bestStatsQueries.resetAll()
    }

}