package me.jfenn.bingo.common.stats

import me.jfenn.bingo.common.config.BingoConfig
import me.jfenn.bingo.common.event.packet.ServerPacketEvents
import me.jfenn.bingo.common.scope.BingoComponent
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.stats.data.GameInfo
import me.jfenn.bingo.common.stats.data.GamePlayerInfo
import me.jfenn.bingo.common.stats.data.GameTeamInfo
import me.jfenn.bingo.common.stats.data.PlayerGameSummary
import me.jfenn.bingo.common.stats.packets.StatsGamePacket
import me.jfenn.bingo.common.utils.json
import me.jfenn.bingo.common.utils.measureTime
import me.jfenn.bingo.platform.IJsonSerializers
import net.minecraft.server.MinecraftServer
import org.slf4j.Logger
import java.time.Duration
import java.time.Instant

internal class WriteStatsService(
    private val log: Logger,
    private val config: BingoConfig,
    private val state: BingoState,
    private val stats: StatsService,
    private val packets: ServerPacketEvents,
    private val server: MinecraftServer,
    private val serializers: IJsonSerializers,
) : BingoComponent() {
    private fun broadcastResult(
        gameInfo: GameInfo,
        teamInfo: GameTeamInfo,
        playerInfo: GamePlayerInfo,
    ) {
        // TODO: if player.uuid is also the server host (in singleplayer/lan), there's no need to send this packet
        val player = server.playerManager.getPlayer(playerInfo.minecraftId) ?: return

        packets.statsGameV1S2C.send(player, StatsGamePacket(
            PlayerGameSummary(
                game = gameInfo,
                team = teamInfo,
                player = playerInfo,
            )
        ))
    }

    private fun writeGameResult(): GameInfo {
        val insertTeams = mutableListOf<GameTeamInfo>()
        val insertPlayers = mutableListOf<GamePlayerInfo>()

        val teams = state.getRegisteredTeams()

        val options = json.encodeToString(state.options)
        val startedAt = state.startedAt ?: run {
            log.error("state.startedAt is null!")
            Instant.now()
        }
        val endedAt = state.endedAt ?: run {
            log.error("state.endedAt is null!")
            Instant.now()
        }
        val insertGame = GameInfo(
            id = state.gameId,
            bingoOptions = options,
            bingoOptionsHash = state.options.getShaHash(),
            startedAt = startedAt,
            endedAt = endedAt,
            duration = state.ingameDuration() ?: Duration.ZERO,
            playerCount = teams.sumOf { it.players.size },
            // the game is a draw if nobody won and there is more than one team
            isDraw = teams.none { it.isWinner() } && teams.size > 1,
            isForfeit = state.isForfeit,
            hostId = config.statsHostId,
        )

        for (team in teams) {
            val insertTeam = GameTeamInfo(
                id = team.key,
                gameId = insertGame.id,
                name = serializers.json.encodeToString(team.getSimpleName()),
                isWinner = team.isWinner()
            ).also { insertTeams.add(it) }

            for (player in team.players) {
                val itemCount = team.completedCards.sumOf { completion ->
                    completion.card.countItems { it.players.containsKey(player.uuid) }
                }
                val insertPlayer = GamePlayerInfo(
                    teamId = insertTeam.id,
                    gameId = insertGame.id,
                    minecraftId = player.uuid,
                    minecraftName = player.name,
                    capturedItems = itemCount,
                )

                broadcastResult(insertGame, insertTeam, insertPlayer)
                insertPlayers.add(insertPlayer)
            }
        }

        stats.insertGame(
            game = insertGame,
            teams = insertTeams,
            players = insertPlayers,
        )
        stats.updateBestStats(
            game = insertGame,
            players = insertPlayers,
        )

        return insertGame
    }

    fun writeGame(): GameInfo {
        return log.measureTime("Writing game result to stats.db...") {
            writeGameResult()
        }
    }
}