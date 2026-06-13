package me.jfenn.bingo.server

import me.jfenn.bingo.common.baseModule
import me.jfenn.bingo.common.commonInit
import me.jfenn.bingo.common.commonModule
import me.jfenn.bingo.common.config.MigrationHandler
import me.jfenn.bingo.platform.scope.BingoKoin
import me.jfenn.bingo.sharedBaseModule
import me.jfenn.bingo.sharedModule
import me.jfenn.bingo.sharedServerModule
import net.fabricmc.api.DedicatedServerModInitializer
import org.koin.core.logger.Level
import org.koin.dsl.koinApplication
import org.koin.logger.SLF4JLogger
import org.slf4j.LoggerFactory

object Main : DedicatedServerModInitializer {

    private val log = LoggerFactory.getLogger(this::class.java)

    override fun onInitializeServer() {
        log.info("Starting Bingo migration runner...")

        koinApplication {
            logger(SLF4JLogger(level = Level.DEBUG))
            modules(sharedBaseModule, baseModule)
        }.also {
            it.koin.get<MigrationHandler>().runMigrations()
            it.close()
        }

        log.info("Starting Bingo Server application...")

        koinApplication {
            BingoKoin.koinApp = this@koinApplication
            logger(SLF4JLogger(level = Level.DEBUG))
            modules(sharedModule, sharedServerModule, commonModule)
        }.also {
            it.koin.commonInit()
        }
    }
}