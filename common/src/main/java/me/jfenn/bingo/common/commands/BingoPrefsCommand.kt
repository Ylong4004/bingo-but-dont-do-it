package me.jfenn.bingo.common.commands

import me.jfenn.bingo.common.Permission
import me.jfenn.bingo.common.config.PlayerSettings
import me.jfenn.bingo.common.config.PlayerSettingsService
import me.jfenn.bingo.common.event.model.PlayerSettingsEvent
import me.jfenn.bingo.common.scope.BingoComponent
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.generated.StringKey
import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.commands.ICommandManager
import me.jfenn.bingo.platform.commands.IExecutionContext
import me.jfenn.bingo.platform.event.IEventBus
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1

class BingoPrefsCommand(
    commandManager: ICommandManager,
    private val text: TextProvider,
    private val eventBus: IEventBus,
) : BingoComponent() {

    data class SettingEntry(
        val field: KProperty1<PlayerSettings, Boolean>,
        val string: StringKey,
    )

    private fun IExecutionContext.toggleBooleanSetting(
        player: IPlayerHandle,
        field: KProperty1<PlayerSettings, Boolean>,
        string: StringKey?,
    ) {
        val handler = scope.get<PlayerSettingsService>()

        val settings = handler.getPlayer(player)
        val newValue = !field.get(settings)
        changeBooleanSetting(player, field, string, newValue)
    }

    private fun IExecutionContext.changeBooleanSetting(
        player: IPlayerHandle,
        field: KProperty1<PlayerSettings, Boolean>,
        string: StringKey?,
        value: Boolean,
    ) {
        val handler = scope.get<PlayerSettingsService>()
        handler.write(player.uuid, field, value, seenTutorial = true)
        eventBus.emit(PlayerSettingsEvent, PlayerSettingsEvent(player))

        if (string != null) {
            sendMessage(
                text.string(StringKey.OptionsNotifyChanged, string, text.boolean(value))
            )
        }

        if (field == PlayerSettings::hideLobbyPrompt && value) {
            sendMessage(text.string(StringKey.InstalledNeverShowAgainMessage))
        }
    }

    init {
        commandManager.register("bingoprefs") {
            requires {
                hasPermission(Permission.CONFIGURE_PLAYER)
            }

            val fields = listOf(
                SettingEntry(PlayerSettings::bossbar, StringKey.PlayerSettingsBossbar),
                SettingEntry(PlayerSettings::scoreboard, StringKey.PlayerSettingsScoreboard),
                SettingEntry(PlayerSettings::scoreboardAutoHide, StringKey.PlayerSettingsScoreboardAutoHide),
                SettingEntry(PlayerSettings::leadingMessages, StringKey.PlayerSettingsMessagesLeading),
                SettingEntry(PlayerSettings::scoreMessages, StringKey.PlayerSettingsMessagesLines),
                SettingEntry(PlayerSettings::itemMessages, StringKey.PlayerSettingsMessagesItems),
                SettingEntry(PlayerSettings::nightVision, StringKey.PlayerSettingsNightVision),
            )

            for ((field, string) in fields) {
                literal(getFieldName(field)) {
                    boolean("value") { newValue ->
                        executes {
                            changeBooleanSetting(playerOrThrow, field, string, getArgument(newValue))
                        }
                    }

                    executes {
                        toggleBooleanSetting(playerOrThrow, field, string)
                    }
                }

                literal(getFieldName(PlayerSettings::hideLobbyPrompt)) {
                    boolean("value") { newValue ->
                        executes {
                            changeBooleanSetting(playerOrThrow, PlayerSettings::hideLobbyPrompt, null, getArgument(newValue))
                        }
                    }
                }
            }
        }
    }

    companion object {
        private fun getFieldName(
            field: KProperty<*>,
        ): String {
            // replace any upper case letter to transform camelCase -> snake_case
            return field.name.replace(Regex("[A-Z]")) { match -> "_" + match.value.lowercase() }
        }

        fun <T> getCommand(
            field: KProperty1<PlayerSettings, T>,
            value: T? = null,
        ): String {
            return when {
                value != null -> "/bingoprefs ${getFieldName(field)} $value"
                else -> "/bingoprefs ${getFieldName(field)}"
            }
        }
    }

}