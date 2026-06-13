package me.jfenn.bingo.client.world

import com.google.common.collect.ImmutableList
import me.jfenn.bingo.client.impl.accessor
import me.jfenn.bingo.common.BINGO_WORLD_PREFIX
import me.jfenn.bingo.common.datapack.LobbyWorldService
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.Screens
import net.minecraft.client.gui.Click
import net.minecraft.client.gui.screen.ConfirmScreen
import net.minecraft.client.gui.screen.world.CreateWorldScreen
import net.minecraft.client.input.MouseInput
import net.minecraft.resource.DataConfiguration
import net.minecraft.resource.DataPackSettings
import net.minecraft.text.TranslatableTextContent
import org.slf4j.Logger

class BingoWorldController(
    private val log: Logger,
    private val worldState: BingoWorldState,
    private val lobbyWorldService: LobbyWorldService,
) {

    companion object {
        const val DATAPACK_FILE = "bingo.zip"
        const val DATAPACK_ID = "file/${DATAPACK_FILE}"
    }

    init {
        ScreenEvents.BEFORE_INIT.register { _, screen, _, _ ->
            if (screen is CreateWorldScreen && worldState.state == ScreenState.CreateBingoWorld) {
                // Add a prefix to the names of bingo worlds
                if (!screen.worldCreator.worldName.startsWith(BINGO_WORLD_PREFIX)) {
                    screen.worldCreator.worldName = "$BINGO_WORLD_PREFIX ${screen.worldCreator.worldName}"
                }
            }
        }

        ScreenEvents.AFTER_INIT.register { client, screen, _, _ ->
            val state = worldState.state

            log.debug(
                "init screen [{}]: {} isApplyingLobbyDataPack={}",
                screen::class.java.simpleName,
                state,
                worldState.isApplyingLobbyDataPack
            )

            if (worldState.isApplyingLobbyDataPack) {
                // this flag prevents infinite recursion, as there is a setScreen call in applyDataPacks
                return@register
            }

            if (screen is CreateWorldScreen && (state == ScreenState.CreateBingoWorld || state == null)) {
                // Copy the built-in lobby data pack to the world creator's temp dir
                // - we want this to happen on every world creation, so that the pack is available
                //   even if the user has not specifically pressed the "BINGO!" button.
                screen.accessor.invokeGetDataPackTempDir()?.let {
                    lobbyWorldService.copyDataPack(it.resolve(DATAPACK_FILE))
                }

                val pair = screen.accessor.invokeGetScannedPack(screen.worldCreator.generatorOptionsHolder.dataConfiguration)
                    ?: return@register

                // this flag prevents infinite recursion, as there is a setScreen call in applyDataPacks
                worldState.isApplyingLobbyDataPack = true
                screen.accessor.invokeApplyDataPacks(pair.second, false) {}
                worldState.isApplyingLobbyDataPack = false

                if (state == ScreenState.CreateBingoWorld) {
                    if (DATAPACK_ID !in pair.second.ids) {
                        log.error("Bingo datapack installation has failed! This will probably cause a crash.")
                    }

                    // actually enable the bingo lobby datapack
                    pair.second.setEnabledProfiles(
                        (pair.second.enabledIds + DATAPACK_ID)
                            .reversed()
                    )

                    val enabled = ImmutableList.copyOf(pair.second.enabledIds)
                    val disabled = pair.second.enabledIds.filter { !enabled.contains(it) }
                    val dataConfiguration = DataConfiguration(
                        DataPackSettings(enabled, disabled),
                        screen.worldCreator.generatorOptionsHolder.dataConfiguration().enabledFeatures()
                    )

                    worldState.state = ScreenState.OpenBingoWorld
                    screen.accessor.invokeValidateDataPacks(pair.second, dataConfiguration) {}

                    return@register
                }
            }

            if (screen is CreateWorldScreen && state == ScreenState.OpenBingoWorld) {
                // Once the world creation screen is reached, immediately press the
                // create world button

                val createWorldButton = Screens.getButtons(screen)
                    .find {
                        val content = it.message.content as? TranslatableTextContent
                        content?.key == "selectWorld.create"
                    }

                worldState.state = ScreenState.ConfirmExperimentalFeatures
                createWorldButton?.onClick(Click(0.0, 0.0, MouseInput(0, 0)), false)
                    ?: log.error("Error: could not find create world button")

                return@register
            }

            if (screen is ConfirmScreen && state == ScreenState.ConfirmExperimentalFeatures) {
                // If there is a prompt to confirm the experimental datapack usage,
                // click yes automatically
                log.warn("Bypassing experimental warnings for a BINGO world")

                val confirmButton = Screens.getButtons(screen)
                    .find {
                        val content = it.message.content as? TranslatableTextContent
                        content?.key == "gui.yes"
                    }

                confirmButton?.onClick(Click(0.0, 0.0, MouseInput(0, 0)), false)
                    ?: log.error("Error: could not find the experimental world features confirm button")
            }
        }
    }

}