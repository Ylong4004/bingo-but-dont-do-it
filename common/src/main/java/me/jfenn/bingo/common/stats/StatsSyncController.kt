package me.jfenn.bingo.common.stats

import me.jfenn.bingo.common.config.BingoConfig
import me.jfenn.bingo.common.event.ScopedEvents
import me.jfenn.bingo.common.event.packet.ServerPacketEvents
import me.jfenn.bingo.common.scope.BingoComponent
import me.jfenn.bingo.common.stats.data.PlayerGameSummary
import me.jfenn.bingo.common.stats.packets.StatsCheckPacket
import me.jfenn.bingo.common.stats.packets.StatsGamePacket
import me.jfenn.bingo.common.stats.packets.StatsIndexPacket
import me.jfenn.bingo.platform.IExecutors
import me.jfenn.bingo.platform.IPlayerHandle
import org.slf4j.Logger
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

internal class StatsSyncController(
    private val log: Logger,
    private val config: BingoConfig,
    private val packets: ServerPacketEvents,
    private val stats: StatsService,
    executors: IExecutors,
    events: ScopedEvents,
) : BingoComponent() {

    private val executor = executors.io

    private val pendingPlayerIds = Collections.newSetFromMap(ConcurrentHashMap<UUID, Boolean>())

    private fun broadcastHash(player: IPlayerHandle) = executor.submit {
        log.info("[StatsSyncController] Broadcasting stats hash to player {}", player.uuid)
        val hash = stats.fetchGamesByPlayerSha512(player.uuid)
        val packet = StatsCheckPacket(hashSha512 = hash)
        packets.statsHashV1S2C.send(player, packet)
    }

    private fun sendKnownIds(player: IPlayerHandle) = executor.submit {
        log.info("[StatsSyncController] Client responded with a hash mismatch - starting sync for player {}", player.uuid)

        for (ids in stats.fetchGamesByPlayer(player.uuid)) {
            val packet = StatsIndexPacket(
                action = StatsIndexPacket.Action.BROADCAST,
                games = ids.toSet(),
            )

            log.info("[StatsSyncController] Broadcasting {} known gameIds to the client", ids.size)
            packets.statsIndexV1S2C.send(player, packet)

            // sleep to avoid overloading the connection
            try {
                Thread.sleep(Random.nextLong(50L, 200L))
            } catch (e: InterruptedException) {
                log.warn("[StatsSyncController] broadcastKnownIds interrupted!")
                break
            }
        }
    }

    private fun checkForMissingIds(player: IPlayerHandle, gameIds: Set<UUID>) = executor.submit {
        val missingIds = stats.checkIfIdsExist(player.uuid, gameIds)

        // if there are ids missing, send the client a packet to request them
        if (missingIds.isNotEmpty()) {
            log.info("[StatsSyncController] Requesting {} missing gameIds from the client", missingIds.size)
            packets.statsIndexV1S2C.send(player, StatsIndexPacket(StatsIndexPacket.Action.REQUEST, missingIds))
        }
    }

    private fun sendRequestedIds(player: IPlayerHandle, gameIds: Set<UUID>) = executor.submit {
        for (gameId in gameIds) {
            val gameSummary = stats.findById(gameId = gameId, playerId = player.uuid)
            if (gameSummary == null) {
                log.error("[StatsSyncController] Requested gameId={}, playerId={} does not exist in the database", gameId, player.uuid)
                continue
            }

            val packet = StatsGamePacket(gameSummary)
            packets.statsGameV1S2C.send(player, packet)

            // sleep to avoid overloading the connection
            try {
                Thread.sleep(Random.nextLong(50L, 200L))
            } catch (e: InterruptedException) {
                log.warn("[StatsSyncController] sendRequestedIds interrupted!")
                break
            }
        }
    }

    private fun insertReceivedGame(game: PlayerGameSummary) {
        stats.insertGame(
            game = game.game,
            teams = listOf(game.team),
            players = listOf(game.player),
        )
    }

    init {
        events.onPlayerChannelRegister { (player) ->
            if (!config.syncStats) return@onPlayerChannelRegister
            if (!packets.statsHashV1S2C.isSupported(player)) return@onPlayerChannelRegister
            // Ensure that broadcastHash is only started once per login
            if (pendingPlayerIds.contains(player.uuid)) return@onPlayerChannelRegister

            pendingPlayerIds.add(player.uuid)
            broadcastHash(player)
        }

        events.onPlayerDisconnect { (player) ->
            pendingPlayerIds.remove(player.uuid)
        }

        events.onPacket(packets.statsHashV1C2S) {
            if (!config.syncStats) return@onPacket
            sendKnownIds(it.player)
        }

        events.onPacket(packets.statsIndexV1C2S) {
            if (!config.syncStats) return@onPacket
            when (it.packet.action) {
                StatsIndexPacket.Action.BROADCAST -> checkForMissingIds(it.player, it.packet.games)
                StatsIndexPacket.Action.REQUEST -> sendRequestedIds(it.player, it.packet.games)
            }
        }

        events.onPacket(packets.statsGameV1C2S) {
            if (!config.syncStats) return@onPacket
            insertReceivedGame(it.packet.game)
        }
    }
}