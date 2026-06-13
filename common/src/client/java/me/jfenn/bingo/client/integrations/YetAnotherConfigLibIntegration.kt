package me.jfenn.bingo.client.integrations

import dev.isxander.yacl3.api.*
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder
import dev.isxander.yacl3.api.controller.EnumControllerBuilder
import dev.isxander.yacl3.api.controller.FloatSliderControllerBuilder
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder
import me.jfenn.bingo.client.common.event.ClientConfigChangedEvent
import me.jfenn.bingo.client.common.hud.screen.BingoCardPlacementScreen
import me.jfenn.bingo.client.common.settings.ClientSettingsService
import me.jfenn.bingo.client.common.sound.ClientSounds
import me.jfenn.bingo.client.common.sound.SoundService
import me.jfenn.bingo.client.platform.IClient
import me.jfenn.bingo.client.platform.renderer.IDrawServiceFactory
import me.jfenn.bingo.common.config.*
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.generated.StringKey
import me.jfenn.bingo.platform.IModEnvironment
import me.jfenn.bingo.platform.event.IEventBus
import net.minecraft.client.gui.screen.Screen
import net.minecraft.text.Text
import net.minecraft.util.Formatting

internal interface YetAnotherConfigLibIntegration {

    fun isInstalled(): Boolean

    fun buildConfigScreen(parent: Screen): Screen?

    companion object {
        fun hasYACL(environment: IModEnvironment) = environment.isModLoaded("yet_another_config_lib_v3")

        fun create(
            environment: IModEnvironment,
            configService: ConfigService,
            config: BingoConfig,
            clientSettings: ClientSettingsService,
            soundService: SoundService,
            text: TextProvider,
            eventBus: IEventBus,
            drawServiceFactory: IDrawServiceFactory,
            cardPlacementScreenFactory: BingoCardPlacementScreen.Factory,
            client: IClient,
        ): YetAnotherConfigLibIntegration {
            return if (hasYACL(environment))
                YetAnotherConfigLibIntegrationImpl(configService, config, clientSettings, soundService, text, eventBus, drawServiceFactory, cardPlacementScreenFactory, client)
            else YetAnotherConfigLibIntegrationDummy()
        }
    }

}

internal class YetAnotherConfigLibIntegrationDummy : YetAnotherConfigLibIntegration {
    override fun isInstalled(): Boolean = false
    override fun buildConfigScreen(parent: Screen): Screen? = null
}

internal class YetAnotherConfigLibIntegrationImpl(
    private val configService: ConfigService,
    private val config: BingoConfig,
    private val clientSettings: ClientSettingsService,
    private val soundService: SoundService,
    private val text: TextProvider,
    private val eventBus: IEventBus,
    private val drawServiceFactory: IDrawServiceFactory,
    private val cardPlacementScreenFactory: BingoCardPlacementScreen.Factory,
    private val client: IClient,
) : YetAnotherConfigLibIntegration {

    private val defaultConfig = BingoConfig()
    private val defaultPlayerSettings = PlayerSettings()

    private val playerSettings get() = clientSettings.getSettings()

    private fun onConfigChanged() {
        eventBus.emit(ClientConfigChangedEvent, Unit)
    }

    override fun isInstalled(): Boolean = true

    override fun buildConfigScreen(parent: Screen): Screen {
        val configCardScale = Option.createBuilder<Float>()
            .name(text.string(StringKey.ConfigCardScale).value)
            .description(
                OptionDescription.of(
                    text.string(StringKey.ConfigCardScaleDescription).value
                )
            )
            .binding(
                defaultConfig.client.cardScale,
                { config.client.cardScale },
                {
                    config.client.cardScale = it
                    configService.writeConfig(config)
                    onConfigChanged()
                }
            )
            .controller {
                FloatSliderControllerBuilder.create(it)
                    .formatValue { value -> Text.literal(String.format("%.0f%%", value*100)) }
                    .range(0.25f, 4.0f)
                    .step(0.05f)
            }
            .build()

        val configCardAlignment = Option.createBuilder<CardAlignment>()
            .name(text.string(StringKey.ConfigCardAlignment).value)
            .description(
                OptionDescription.of(
                    text.string(StringKey.ConfigCardAlignmentDescription).value
                )
            )
            .binding(
                defaultConfig.client.cardAlignment,
                { config.client.cardAlignment },
                {
                    config.client.cardAlignment = it
                    configService.writeConfig(config)
                }
            )
            .controller {
                EnumControllerBuilder.create(it)
                    .enumClass(CardAlignment::class.java)
                    .formatValue { value -> text.string(value.string).value }
            }
            .build()

        val configCardOffsetX = Option.createBuilder<Int>()
            .name(text.string(StringKey.ConfigCardOffsetX).value)
            .description(
                OptionDescription.of(
                    text.string(StringKey.ConfigCardOffsetXDescription).value
                )
            )
            .binding(
                defaultConfig.client.cardOffsetX,
                { config.client.cardOffsetX },
                {
                    config.client.cardOffsetX = it
                    configService.writeConfig(config)
                }
            )
            .controller {
                IntegerSliderControllerBuilder.create(it)
                    .range(0, drawServiceFactory.window.scaledWindowWidth/2)
                    .step(1)
            }
            .build()

        val configCardOffsetY = Option.createBuilder<Int>()
            .name(text.string(StringKey.ConfigCardOffsetY).value)
            .description(
                OptionDescription.of(
                    text.string(StringKey.ConfigCardOffsetYDescription).value
                )
            )
            .binding(
                defaultConfig.client.cardOffsetY,
                { config.client.cardOffsetY },
                {
                    config.client.cardOffsetY = it
                    configService.writeConfig(config)
                }
            )
            .controller {
                IntegerSliderControllerBuilder.create(it)
                    .range(0, drawServiceFactory.window.scaledWindowHeight/2)
                    .step(1)
            }
            .build()

        return YetAnotherConfigLib.createBuilder()
            .title(text.string(StringKey.FullName).value)
            .category(
                ConfigCategory.createBuilder()
                    .name(text.string(StringKey.ConfigGui).value)
                    .tooltip(text.string(StringKey.ConfigGuiTooltip).value)
                    .options(listOf(
                        Option.createBuilder<Boolean>()
                            .name(text.string(StringKey.ConfigQuickStartButton).value)
                            .description(
                                OptionDescription.of(
                                    text.string(StringKey.ConfigQuickStartButtonDescription).value,
                                    Text.empty(),
                                    text.string(StringKey.ConfigQuickStartButtonDescription2).value,
                                )
                            )
                            .binding(
                                defaultConfig.client.showQuickStartButton,
                                { config.client.showQuickStartButton },
                                {
                                    config.client.showQuickStartButton = it
                                    configService.writeConfig(config)
                                }
                            )
                            .controller { BooleanControllerBuilder.create(it).coloured(true) }
                            .build()
                    ))
                    .group(
                        OptionGroup.createBuilder()
                            .name(text.string(StringKey.ConfigGuiHudSettings).value)
                            .options(listOf(
                                Option.createBuilder<Boolean>()
                                    .name(text.string(StringKey.ConfigEnableClientHud).value)
                                    .description(
                                        OptionDescription.of(
                                            text.string(StringKey.ConfigEnableClientHudDescription).value
                                        )
                                    )
                                    .binding(
                                        defaultConfig.client.enableHud,
                                        { config.client.enableHud },
                                        {
                                            config.client.enableHud = it
                                            configService.writeConfig(config)
                                        }
                                    )
                                    .controller { BooleanControllerBuilder.create(it).coloured(true) }
                                    .build(),
                                ButtonOption.createBuilder()
                                    .name(
                                        text.string(StringKey.ConfigCardPosition).value
                                    )
                                    .text(text.string(StringKey.ConfigCardPositionEdit).value)
                                    .description(OptionDescription.of(
                                        text.string(StringKey.ConfigCardPositionDescription).value,
                                        text.string(StringKey.ConfigCardPositionHelpMove).value,
                                        text.string(StringKey.ConfigCardPositionHelpResize).value,
                                    ))
                                    .action { parent, _ ->
                                        client.screen = cardPlacementScreenFactory.create { result ->
                                            if (result is BingoCardPlacementScreen.Result.Ok) {
                                                config.client.cardScale = result.cardScale
                                                config.client.cardAlignment = result.cardAlignment
                                                config.client.cardOffsetX = result.cardOffsetX
                                                config.client.cardOffsetY = result.cardOffsetY
                                                configService.writeConfig(config)
                                            }
                                            client.screen = parent
                                            if (result is BingoCardPlacementScreen.Result.Ok) {
                                                configCardScale.forgetPendingValue()
                                                configCardAlignment.forgetPendingValue()
                                                configCardOffsetX.forgetPendingValue()
                                                configCardOffsetY.forgetPendingValue()
                                            }
                                        }
                                    }
                                    .build(),
                                Option.createBuilder<CardOverlap>()
                                    .name(text.string(StringKey.ConfigCardOverlap).value)
                                    .description(
                                        OptionDescription.of(
                                            text.string(StringKey.ConfigCardOverlapDescription).value
                                        )
                                    )
                                    .binding(
                                        defaultConfig.client.cardOverlap,
                                        { config.client.cardOverlap },
                                        {
                                            config.client.cardOverlap = it
                                            configService.writeConfig(config)
                                        }
                                    )
                                    .controller {
                                        EnumControllerBuilder.create(it)
                                            .enumClass(CardOverlap::class.java)
                                            .formatValue { value -> text.string(value.string).value }
                                    }
                                    .build(),
                                Option.createBuilder<Boolean>()
                                    .name(text.string(StringKey.ConfigShowMultipleCards).value)
                                    .description(
                                        OptionDescription.of(
                                            text.string(StringKey.ConfigShowMultipleCardsDescription).value
                                        )
                                    )
                                    .binding(
                                        defaultConfig.client.showMultipleCards,
                                        { config.client.showMultipleCards },
                                        {
                                            config.client.showMultipleCards = it
                                            configService.writeConfig(config)
                                            onConfigChanged()
                                        }
                                    )
                                    .controller { BooleanControllerBuilder.create(it) }
                                    .build(),
                                Option.createBuilder<Boolean>()
                                    .name(text.string(StringKey.ConfigShowTeamOutlines).value)
                                    .description(
                                        OptionDescription.of(
                                            text.string(StringKey.ConfigShowTeamOutlinesDescription).value
                                        )
                                    )
                                    .binding(
                                        defaultConfig.client.cardTeamOutlines,
                                        { config.client.cardTeamOutlines },
                                        {
                                            config.client.cardTeamOutlines = it
                                            configService.writeConfig(config)
                                            onConfigChanged()
                                        }
                                    )
                                    .controller { BooleanControllerBuilder.create(it) }
                                    .build(),
                                Option.createBuilder<Boolean>()
                                    .name(text.string(StringKey.ConfigHideOnF3).value)
                                    .description(
                                        OptionDescription.of(
                                            text.string(StringKey.ConfigHideOnF3Description).value
                                        )
                                    )
                                    .binding(
                                        defaultConfig.client.hideOnF3,
                                        { config.client.hideOnF3 },
                                        {
                                            config.client.hideOnF3 = it
                                            configService.writeConfig(config)
                                        }
                                    )
                                    .controller { BooleanControllerBuilder.create(it) }
                                    .build(),
                                Option.createBuilder<Boolean>()
                                    .name(text.string(StringKey.ConfigHideOnChat).value)
                                    .description(
                                        OptionDescription.of(
                                            text.string(StringKey.ConfigHideOnChatDescription).value
                                        )
                                    )
                                    .binding(
                                        defaultConfig.client.hideOnChat,
                                        { config.client.hideOnChat },
                                        {
                                            config.client.hideOnChat = it
                                            configService.writeConfig(config)
                                        }
                                    )
                                    .controller { BooleanControllerBuilder.create(it) }
                                    .build(),
                                Option.createBuilder<Boolean>()
                                    .name(text.string(StringKey.ConfigCardPausesGame).value)
                                    .description(
                                        OptionDescription.of(
                                            text.string(StringKey.ConfigCardPausesGameDescription).value
                                        )
                                    )
                                    .binding(
                                        defaultConfig.client.cardPausesGame,
                                        { config.client.cardPausesGame },
                                        {
                                            config.client.cardPausesGame = it
                                            configService.writeConfig(config)
                                        }
                                    )
                                    .controller { BooleanControllerBuilder.create(it) }
                                    .build(),
                            ))
                            .build()
                    )
                    .group(
                        OptionGroup.createBuilder()
                            .collapsed(false)
                            .name(text.string(StringKey.PlayerSettingsMessages).value)
                            .option(
                                Option.createBuilder<Int>()
                                    .name(text.string(StringKey.PlayerSettingsMessagesDuration).value)
                                    .description(
                                        OptionDescription.of(
                                            text.string(StringKey.PlayerSettingsMessagesDurationDescription).value
                                        )
                                    )
                                    .binding(
                                        defaultConfig.client.messageDurationSeconds,
                                        { config.client.messageDurationSeconds },
                                        {
                                            config.client.messageDurationSeconds = it
                                            configService.writeConfig(config)
                                        }
                                    )
                                    .controller {
                                        IntegerSliderControllerBuilder.create(it)
                                            .range(1, 30)
                                            .step(1)
                                    }
                                    .build(),
                            )
                            .option(
                                Option.createBuilder<Float>()
                                    .name(text.string(StringKey.PlayerSettingsMessagesScale).value)
                                    .description(
                                        OptionDescription.of(
                                            text.string(StringKey.PlayerSettingsMessagesScaleDescription).value
                                        )
                                    )
                                    .binding(
                                        defaultConfig.client.messageScale,
                                        { config.client.messageScale },
                                        {
                                            config.client.messageScale = it
                                            configService.writeConfig(config)
                                        }
                                    )
                                    .controller {
                                        FloatSliderControllerBuilder.create(it)
                                            .formatValue { value -> Text.literal(String.format("%.0f%%", value*100)) }
                                            .range(0.25f, 4.0f)
                                            .step(0.05f)
                                    }
                                    .build(),
                            )
                            .option(
                                Option.createBuilder<Boolean>()
                                    .name(text.string(StringKey.PlayerSettingsMessagesLeading).value)
                                    .description(
                                        OptionDescription.of(
                                            text.string(StringKey.PlayerSettingsMessagesLeadingDescription).value
                                        )
                                    )
                                    .binding(
                                        defaultPlayerSettings.leadingMessages,
                                        { playerSettings.leadingMessages },
                                        {
                                            clientSettings.update(playerSettings.copy(leadingMessages = it))
                                        }
                                    )
                                    .controller { BooleanControllerBuilder.create(it) }
                                    .build()
                            )
                            .option(
                                Option.createBuilder<Boolean>()
                                    .name(text.string(StringKey.PlayerSettingsMessagesLines).value)
                                    .description(
                                        OptionDescription.of(
                                            text.string(StringKey.PlayerSettingsMessagesLinesDescription).value
                                        )
                                    )
                                    .binding(
                                        defaultPlayerSettings.scoreMessages,
                                        { playerSettings.scoreMessages },
                                        {
                                            clientSettings.update(playerSettings.copy(scoreMessages = it))
                                        }
                                    )
                                    .controller { BooleanControllerBuilder.create(it) }
                                    .build(),
                            )
                            .option(
                                Option.createBuilder<Boolean>()
                                    .name(text.string(StringKey.PlayerSettingsMessagesItems).value)
                                    .description(
                                        OptionDescription.of(
                                            text.string(StringKey.PlayerSettingsMessagesItemsDescription).value
                                        )
                                    )
                                    .binding(
                                        defaultPlayerSettings.itemMessages,
                                        { playerSettings.itemMessages },
                                        {
                                            clientSettings.update(playerSettings.copy(itemMessages = it))
                                        }
                                    )
                                    .controller { BooleanControllerBuilder.create(it) }
                                    .build(),
                            )
                            .option(
                                Option.createBuilder<Boolean>()
                                    .name(text.string(StringKey.PlayerSettingsMessagesShowOtherTeams).value)
                                    .description(
                                        OptionDescription.of(
                                            text.string(StringKey.PlayerSettingsMessagesShowOtherTeamsDescription).value
                                        )
                                    )
                                    .binding(
                                        defaultConfig.client.messageFromOtherTeams,
                                        { config.client.messageFromOtherTeams },
                                        {
                                            config.client.messageFromOtherTeams = it
                                            configService.writeConfig(config)
                                        }
                                    )
                                    .controller { BooleanControllerBuilder.create(it) }
                                    .build(),
                            )
                            .build()
                    )
                    .group(
                        OptionGroup.createBuilder()
                            .collapsed(true)
                            .name(text.string(StringKey.ConfigCardPositionManual).value)
                            .options(listOf(configCardScale, configCardAlignment, configCardOffsetX, configCardOffsetY))
                            .build()
                    )
                    .build()
            )
            .category(
                ConfigCategory.createBuilder()
                    .name(text.string(StringKey.PlayerSettings).value)
                    .tooltip(text.string(StringKey.PlayerSettingsTooltip).value)
                    .option(
                        Option.createBuilder<Boolean>()
                            .name(text.string(StringKey.PlayerSettingsBossbar).value)
                            .description(
                                OptionDescription.of(
                                    text.string(StringKey.PlayerSettingsBossbarDescription).value
                                )
                            )
                            .binding(
                                defaultPlayerSettings.bossbar,
                                { playerSettings.bossbar },
                                {
                                    clientSettings.update(playerSettings.copy(bossbar = it))
                                }
                            )
                            .controller { BooleanControllerBuilder.create(it) }
                            .build()
                    )
                    .option(
                        Option.createBuilder<Boolean>()
                            .name(text.string(StringKey.PlayerSettingsScoreboard).value)
                            .description(
                                OptionDescription.of(
                                    text.string(StringKey.PlayerSettingsScoreboardDescription).value
                                )
                            )
                            .binding(
                                defaultPlayerSettings.scoreboard,
                                { playerSettings.scoreboard },
                                {
                                    clientSettings.update(playerSettings.copy(scoreboard = it))
                                }
                            )
                            .controller { BooleanControllerBuilder.create(it) }
                            .build()
                    )
                    .option(
                        Option.createBuilder<Boolean>()
                            .name(text.string(StringKey.PlayerSettingsNightVision).value)
                            .description(
                                OptionDescription.of(
                                    text.string(StringKey.PlayerSettingsNightVisionDescription).value
                                )
                            )
                            .binding(
                                defaultPlayerSettings.nightVision,
                                { playerSettings.nightVision },
                                {
                                    clientSettings.update(playerSettings.copy(nightVision = it))
                                }
                            )
                            .controller { BooleanControllerBuilder.create(it) }
                            .build(),
                    )
                    .build(),

            )
            .category(
                ConfigCategory.createBuilder()
                    .name(text.string(StringKey.StatsSettings).value)
                    .tooltip(text.string(StringKey.StatsSettingsTooltip).value)
                    .option(
                        Option.createBuilder<Boolean>()
                            .name(text.string(StringKey.StatsSettingsSync).value)
                            .description(
                                OptionDescription.of(
                                    text.string(StringKey.StatsSettingsSyncDescription).value
                                )
                            )
                            .binding(
                                defaultConfig.syncStats,
                                { config.syncStats },
                                {
                                    config.syncStats = it
                                    configService.writeConfig(config)
                                }
                            )
                            .controller { BooleanControllerBuilder.create(it) }
                            .build()
                    )
                    .build()
            )
            .category(
                ConfigCategory.createBuilder()
                    .name(text.string(StringKey.SoundSettings).value)
                    .tooltip(text.string(StringKey.SoundSettingsTooltip).value)
                    .groups(
                        ClientSounds.Key.entries.map { sound ->
                            val soundId = sound.name.lowercase()
                            val soundString = StringKey.entries.find { it.key == "bingo.sound.${soundId}" }!!

                            val sliderOption = Option.createBuilder<Float>()
                                .name(text.string(StringKey.SoundSettingsVolume).value)
                                .description(OptionDescription.of(
                                    text.string(StringKey.SoundSettingsCategoryDescription).formatted(Formatting.YELLOW).value
                                ))
                                .controller {
                                    FloatSliderControllerBuilder.create(it)
                                        .range(0f, 1.5f)
                                        .step(0.01f)
                                        .formatValue { value -> Text.literal((value * 100f).toInt().toString() + "%") }
                                }
                                .binding(
                                    defaultConfig.client.getSoundVolume(soundId),
                                    { config.client.getSoundVolume(soundId) },
                                    {
                                        config.client.soundVolumes[soundId] = it
                                        configService.writeConfig(config)
                                    }
                                )
                                .build()

                            val buttonOption = ButtonOption.createBuilder()
                                .name(
                                    text.string(StringKey.SoundSettingsTestSound).value
                                )
                                .description(OptionDescription.of(
                                    text.string(StringKey.SoundSettingsCategoryDescription).formatted(Formatting.YELLOW).value
                                ))
                                .action { _, _ ->
                                    soundService.play(sound, actualVolume = sliderOption.pendingValue())
                                }
                                .build()

                            OptionGroup.createBuilder()
                                .name(text.string(soundString).value)
                                .description(OptionDescription.of(
                                    text.string(StringKey.SoundSettingsCategoryDescription).formatted(Formatting.YELLOW).value
                                ))
                                .collapsed(false)
                                .option(sliderOption)
                                .option(buttonOption)
                                .build()
                        }
                    )
                    .build()
            )
            .build()
            .generateScreen(parent)
    }
}
