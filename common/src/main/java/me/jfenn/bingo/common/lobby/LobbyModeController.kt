package me.jfenn.bingo.common.lobby

import me.jfenn.bingo.common.Permission
import me.jfenn.bingo.common.URL_WIKI_GETTING_STARTED
import me.jfenn.bingo.common.URL_WIKI_SERVER_SETUP
import me.jfenn.bingo.common.commands.BingoPrefsCommand
import me.jfenn.bingo.common.config.PlayerSettings
import me.jfenn.bingo.common.config.PlayerSettingsService
import me.jfenn.bingo.common.event.ScopedEvents
import me.jfenn.bingo.common.game.GameCommands.Companion.START_COMMAND
import me.jfenn.bingo.common.lobby.BingoLobbyCommand.Companion.LOBBY_COMMAND
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.generated.StringKey
import me.jfenn.bingo.integrations.permissions.IPermissionsApi
import me.jfenn.bingo.platform.IMinecraftServer
import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.dialog.*
import me.jfenn.bingo.platform.text.TextAction
import net.minecraft.util.Formatting

internal class LobbyModeController(
    events: ScopedEvents,
    private val state: BingoState,
    private val server: IMinecraftServer,
    private val lobbyModeService: LobbyModeService,
    private val playerSettingsService: PlayerSettingsService,
    private val dialogManager: IDialogManager,
    private val permissions: IPermissionsApi,
    private val text: TextProvider,
) {
    private val openServerSetup get() = text.literal("  \uD83D\uDD17 ")
        .append(
            text.string(StringKey.InstalledServerSetupWiki)
                .formatted(Formatting.GREEN, Formatting.UNDERLINE)
        )
        .apply {
            setClickEvent(TextAction.OpenUrl(URL_WIKI_SERVER_SETUP))
        }

    private val openGettingStarted get() = text.literal("  \uD83D\uDD17 ")
        .append(
            text.string(StringKey.InstalledGettingStartedWiki)
                .formatted(Formatting.GREEN, Formatting.UNDERLINE)
        )
        .apply {
            setClickEvent(TextAction.OpenUrl(URL_WIKI_GETTING_STARTED))
        }

    private val neverShowAgain get() = text.literal("  > ")
        .append(
            text.string(StringKey.InstalledNeverShowAgain)
                .formatted(Formatting.GRAY, Formatting.UNDERLINE)
        )
        .apply {
            setClickEvent(
                TextAction.RunCommand(
                    BingoPrefsCommand.getCommand(PlayerSettings::hideLobbyPrompt, true)
                )
            )
        }

    private fun createHints() = sequence {
        yield(text.string(StringKey.InstalledSurvivalMode_1))
        yield(text.string(StringKey.InstalledSurvivalMode_2))
        if (server.isDedicated) {
            yield(text.string(StringKey.InstalledStartAGameServer, LOBBY_COMMAND, START_COMMAND))
            yield(openServerSetup)
        } else {
            yield(text.string(StringKey.InstalledStartAGameSingleplayer, START_COMMAND))
            yield(openGettingStarted)
        }
    }

    private fun createHintsDialog(): IDialogHandle? {
        val builder = when {
            server.isDedicated -> dialogManager.multiActionBuilder()
            else -> dialogManager.noticeBuilder()
        }
            ?: return null

        builder.title = text.string(StringKey.FullName)

        createHints().forEach { builder.addText(it) }

        if (builder is IMultiActionDialogBuilder) {
            builder.addAction(
                text.string(StringKey.InstalledActivateLobbyMode),
                IDialogAction.RunCommand(LOBBY_COMMAND)
            )
        }

        builder.addInput(
            IDialogInput.Boolean(
                "never_show_again",
                text.string(StringKey.InstalledNeverShowAgain),
            )
        )

        val closeAction = IDialogAction.DynamicRunCommand(
            BingoPrefsCommand.getCommand(PlayerSettings::hideLobbyPrompt) + " $(never_show_again)"
        )

        when (builder) {
            is INoticeDialogBuilder -> builder.setAction(text.string(StringKey.GuiClose), closeAction)
            is IMultiActionDialogBuilder -> builder.setExitAction(text.string(StringKey.GuiClose), closeAction)
        }

        return builder.build()
    }

    private fun sendLobbyModeHints(player: IPlayerHandle) {
        createHintsDialog()?.let {
            dialogManager.showDialog(player, it)
            return
        }

        createHints().forEach { player.sendMessage(it) }
        player.sendMessage(neverShowAgain)
    }

    private fun sendLocalSingleplayerHints(player: IPlayerHandle) {
        createHintsDialog()?.let {
            dialogManager.showDialog(player, it)
            return
        }

        createHints().forEach { player.sendMessage(it) }
        player.sendMessage(neverShowAgain)
    }

    init {
        events.onPlayerJoin { (player) ->
            if (state.isLobbyMode) return@onPlayerJoin
            if (playerSettingsService.getPlayer(player).hideLobbyPrompt) return@onPlayerJoin

            if (server.isDedicated && lobbyModeService.canUseLobbyCommand(player))
                sendLobbyModeHints(player)
            if (server.isSingleplayer && permissions.hasPermission(player, Permission.CONFIGURE_GAME))
                sendLocalSingleplayerHints(player)
        }
    }
}