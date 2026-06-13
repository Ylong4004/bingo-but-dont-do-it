package me.jfenn.bingo.integrations.chunky

import me.jfenn.bingo.platform.IModEnvironment
import me.jfenn.bingo.platform.config.IConfigManager
import me.jfenn.bingo.platform.config.read
import me.jfenn.bingo.platform.config.write
import org.koin.core.scope.Scope

class ChunkyFactory : IChunkyApiFactory {
    companion object {
        private const val CONFIG_PATH = "yet-another-minecraft-bingo/chunky.json"
    }

    override fun create(
        scope: Scope,
    ): IChunkyApi {
        val environment = scope.get<IModEnvironment>()
        val configManager = scope.get<IConfigManager>()

        return if (environment.isModLoaded("chunky")) {
            val config = try {
                configManager.read(CONFIG_PATH)
            } catch (e: Throwable) {
                ChunkyConfig()
            }
            configManager.write(CONFIG_PATH, config)

            if (config.enabled) {
                ChunkyImpl(
                    server = scope.get(),
                    config = config,
                    commandRunner = scope.get(),
                    log = scope.get(),
                )
            } else {
                DummyChunky
            }
        } else {
            DummyChunky
        }
    }
}