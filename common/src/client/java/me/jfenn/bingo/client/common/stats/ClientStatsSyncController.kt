package me.jfenn.bingo.client.common.stats

import me.jfenn.bingo.client.common.packet.ClientPacketEvents
import me.jfenn.bingo.client.platform.ISessionAccessor
import me.jfenn.bingo.common.config.BingoConfig
import me.jfenn.bingo.common.scope.BingoComponent
import me.jfenn.bingo.common.stats.StatsService
import me.jfenn.bingo.common.stats.data.PlayerGameSummary
import me.jfenn.bingo.common.stats.packets.StatsCheckPacket
import me.jfenn.bingo.common.stats.packets.StatsGamePacket
import me.jfenn.bingo.common.stats.packets.StatsIndexPacket
import me.jfenn.bingo.platform.IExecutors
import me.jfenn.bingo.platform.event.IEventBus
import org.slf4j.Logger
import java.util.*
import kotlin.random.Random

internal class ClientStatsSyncController(
    private val log: Logger,
    private val config: BingoConfig,
    private val packets: ClientPacketEvents,
    private val stats: StatsService,
    executors: IExecutors,
    sessionAccessor: ISessionAccessor,
    eventBus: IEventBus,
) : BingoComponent() {

    private val playerUuid = sessionAccessor.getPlayerUuid()
        ?: UUID.fromString("00000000-0000-0000-0000-000000000000")

    private val executor = executors.io

    private fun sendKnownIdsIfMismatch(check: StatsCheckPacket) = executor.submit {
        val hash = stats.fetchGamesByPlayerSha512(playerUuid)
        if (hash == check.hashSha512) {
            log.info("[ClientStatsSyncController] Hash matched! No sync required.")
            return@submit
        }

        log.info("[ClientStatsSyncController] Hash mismatch - starting sync...")
        // Send a hash packet back to the server so that it knows the hashes are mismatched
        packets.statsHashV1C2S.send(StatsCheckPacket(hashSha512 = hash))

        for (ids in stats.fetchGamesByPlayer(playerUuid)) {
            val packet = StatsIndexPacket(
                action = StatsIndexPacket.Action.BROADCAST,
                games = ids.toSet(),
            )

            log.info("[ClientStatsSyncController] Broadcasting {} known gameIds to the server", ids.size)
            packets.statsIndexV1C2S.send(packet)

            // sleep to avoid overloading the connection
            try {
                Thread.sleep(Random.nextLong(50L, 200L))
            } catch (e: InterruptedException) {
                log.warn("[ClientStatsSyncController] broadcastKnownIds interrupted!")
                break
            }
        }
    }

    private fun checkForMissingIds(gameIds: Set<UUID>) = executor.submit {
        val missingIds = stats.checkIfIdsExist(playerUuid, gameIds)

        // if there are ids missing, send the client a packet to request them
        if (missingIds.isNotEmpty()) {
            log.info("[ClientStatsSyncController] Requesting {} missing gameIds from the server", missingIds.size)
            packets.statsIndexV1C2S.send(StatsIndexPacket(StatsIndexPacket.Action.REQUEST, missingIds))
        }
    }

    private fun sendRequestedIds(gameIds: Set<UUID>) = executor.submit {
        for (gameId in gameIds) {
            val gameSummary = stats.findById(gameId = gameId, playerId = playerUuid)
            if (gameSummary == null) {
                log.error("[ClientStatsSyncController] Requested gameId={}, playerId={} does not exist in the database", gameId, playerUuid)
                continue
            }

            log.info("[ClientStatsSyncController] Sending requested gameId={}", gameId)
            val packet = StatsGamePacket(gameSummary)
            packets.statsGameV1C2S.send(packet)

            // sleep to avoid overloading the connection
            try {
                Thread.sleep(Random.nextLong(50L, 200L))
            } catch (e: InterruptedException) {
                log.warn("[ClientStatsSyncController] sendRequestedIds interrupted!")
                break
            }
        }
    }

    private fun insertReceivedGame(game: PlayerGameSummary) {
        if (game.player.minecraftId != playerUuid) {
            log.error("[ClientStatsSyncController] Received game info is for playerId={}, which doesn't match my playerId={}...", game.player.minecraftId, playerUuid)
            return
        }

        stats.insertGame(
            game = game.game,
            teams = listOf(game.team),
            players = listOf(game.player),
        )
    }

    init {
        eventBus.register(packets.statsHashV1S2C) {
            if (!config.syncStats) return@register
            sendKnownIdsIfMismatch(it.packet)
        }

        eventBus.register(packets.statsIndexV1S2C) {
            if (!config.syncStats) return@register
            when (it.packet.action) {
                StatsIndexPacket.Action.BROADCAST -> checkForMissingIds(it.packet.games)
                StatsIndexPacket.Action.REQUEST -> sendRequestedIds(it.packet.games)
            }
        }

        eventBus.register(packets.statsGameV1S2C) {
            if (!config.syncStats) return@register
            insertReceivedGame(it.packet.game)
        }
    }
}