package me.jfenn.bingo.common.config

import me.jfenn.bingo.common.MOD_ID_BINGO
import me.jfenn.bingo.common.options.BingoOptions
import me.jfenn.bingo.platform.config.IConfigManager

class ConfigService(
    configManager: IConfigManager,
) {

    var config: BingoConfig by configManager.config("$MOD_ID_BINGO/config.json")
    var options: BingoOptions by configManager.config("$MOD_ID_BINGO/game-options.json")
    var optionsDefault: BingoOptions by configManager.config("$MOD_ID_BINGO/game-options-default.json")
    var files: TrackedFiles by configManager.config("$MOD_ID_BINGO/files.json")

    fun writeConfig(config: BingoConfig) {
        this.config = config
    }

    fun writeOptions(options: BingoOptions) {
        this.options = options
    }

}