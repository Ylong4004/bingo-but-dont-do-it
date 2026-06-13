package me.jfenn.bingo.common.lobby

import me.jfenn.bingo.common.Permission
import me.jfenn.bingo.common.config.ConfigService
import me.jfenn.bingo.common.lobby.BingoLobbyCommand.Companion.CONFIRM_COMMAND
import me.jfenn.bingo.common.lobby.BingoLobbyCommand.Companion.LOBBY_COMMAND
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.common.utils.minutes
import me.jfenn.bingo.generated.StringKey
import me.jfenn.bingo.integrations.permissions.IPermissionsApi
import me.jfenn.bingo.platform.IPlayerHandle
import net.minecraft.server.MinecraftServer
import net.minecraft.util.Formatting
import org.slf4j.Logger
import java.time.Instant
import java.util.*

internal class LobbyModeService(
    private val server: MinecraftServer,
    private val permissions: IPermissionsApi,
    private val configService: ConfigService,
    private val text: TextProvider,
    private val log: Logger,
) {
    private var readWarnings = mutableMapOf<UUID, Instant>()

    fun canUseLobbyCommand(player: IPlayerHandle) = server.isDedicated && permissions.hasPermission(player, Permission.CONFIGURE_LOBBY)

    fun getWarnings() = sequence {
        text.string(StringKey.CommandLobbyWarning1)
            .formatted(Formatting.GOLD)
            .let { yield(it) }
        text.string(StringKey.CommandLobbyWarning2)
            .formatted(Formatting.GOLD)
            .let { yield(it) }
        yield(text.empty())
        text.string(StringKey.CommandLobbyWarning3)
            .formatted(Formatting.GOLD)
            .let { yield(it) }
    }

    fun getCommandWarnings() = sequence {
        yield(text.empty())
        yieldAll(getWarnings())
        yield(text.empty())
        text.string(StringKey.CommandLobbyWarning4, CONFIRM_COMMAND)
            .formatted(Formatting.GRAY)
            .let { yield(it) }
    }

    fun onPlayerWarned(uuid: UUID) {
        readWarnings[uuid] = Instant.now()
    }

    fun acceptWarnings(uuid: UUID, playerName: String) = sequence {
        val playerId = uuid
        val readWarning = readWarnings[playerId]

        if (readWarning != null && readWarning > Instant.now() - 5.minutes) {
            yield(text.string(StringKey.CommandLobbyRestarting))

            log.info("[BingoLobbyCommand] $playerName used '$CONFIRM_COMMAND' and has accepted the warnings.")

            configService.writeConfig(configService.config.apply {
                server.isLobbyMode = true
            })
            server.stop(false)
        } else {
            yield(text.string(StringKey.CommandLobbyMustRun, LOBBY_COMMAND).formatted(
                Formatting.RED
            ))
            throw IllegalArgumentException("Has not read the warnings")
        }
    }
}