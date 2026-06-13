package me.jfenn.bingo.integrations.vanish

import me.jfenn.bingo.platform.IModEnvironment
import me.jfenn.bingo.platform.config.IConfigManager
import me.jfenn.bingo.platform.config.read
import me.jfenn.bingo.platform.config.write
import org.koin.core.scope.Scope

class VanishApiFactory : IVanishApiFactory {
    companion object {
        private const val CONFIG_PATH = "yet-another-minecraft-bingo/vanish.json"
    }

    override fun create(scope: Scope): IVanishApi? {
        val environment = scope.get<IModEnvironment>()
        val configManager = scope.get<IConfigManager>()

        if (!environment.isModLoaded("vanish"))
            return null

        val config = try {
            configManager.read(CONFIG_PATH)
        } catch (e: Throwable) {
            VanishConfig()
        }
        configManager.write(CONFIG_PATH, config)

        return if (config.enabled) {
            VanishApiImpl()
        } else {
            null
        }
    }
}