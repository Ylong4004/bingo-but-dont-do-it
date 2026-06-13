package me.jfenn.bingo.common.stats

import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import com.zaxxer.hikari.HikariDataSource
import me.jfenn.bingo.common.MOD_ID_BINGO
import me.jfenn.bingo.common.config.BingoConfig
import me.jfenn.bingo.common.utils.measureTime
import me.jfenn.bingo.platform.IModEnvironment
import me.jfenn.bingo.platform.event.IEventBus
import me.jfenn.bingo.platform.event.model.ApplicationCloseEvent
import me.jfenn.bingo.sql.BingoDatabase
import org.slf4j.Logger
import kotlin.io.path.absolutePathString
import kotlin.math.max

class ConnectionFactory(
    private val log: Logger,
    private val config: BingoConfig,
    environment: IModEnvironment,
    eventBus: IEventBus,
) {

    private val defaultUrl = environment.configDir
        .absolutePathString()
        .let { configDir ->
            "jdbc:sqlite:$configDir/$MOD_ID_BINGO/bingo.db"
        }

    private val url = config.databaseUrl ?: defaultUrl

    private val dataSource = HikariDataSource().apply {
        jdbcUrl = url
        username = config.databaseUser ?: ""
        password = config.databasePass ?: ""
    }

    private fun doMigrations() {
        val driver = dataSource.asJdbcDriver()
        val connection = dataSource.connection

        var oldVersion = try {
            connection.createStatement()
                .use { it.executeQuery("PRAGMA user_version").getLong("user_version") }
        } catch (e: Throwable) {
            log.error("Error determining user_version", e)
            0L
        }

        // legacy support for flyway's migration table
        if (oldVersion == 0L) {
            val flywayVersion = try {
                connection.createStatement()
                    .use {
                        it.executeQuery("SELECT max(version) AS flyway_version FROM flyway_schema_history")
                            .getLong("flyway_version")
                    }
                    .let { if (it > 0) it + 1 else it }
            } catch (e: Throwable) {
                0L
            }

            oldVersion = max(oldVersion, flywayVersion)
        }

        val newVersion = BingoDatabase.Schema.version
        log.info("[ConnectionFactory] Migrating database from version $oldVersion to $newVersion...")

        if (oldVersion <= 0L) {
            BingoDatabase.Schema.create(driver)
        } else {
            BingoDatabase.Schema.migrate(
                driver = driver,
                oldVersion = oldVersion,
                newVersion = newVersion,
            )
        }

        connection.createStatement()
            .use { it.execute("PRAGMA user_version = $newVersion") }
    }

    fun create(): BingoDatabase {
        log.measureTime("Running database migrations...") {
            try {
                doMigrations()
            } catch (e: Throwable) {
                log.error("[ConnectionFactory] Unable to run migrations - things might be broken!", e)
            }
        }

        return BingoDatabase(dataSource.asJdbcDriver())
    }

    fun close() {
        log.info("[ConnectionFactory] Closing stats data source...")
        dataSource.close()
    }

    init {
        eventBus.register(ApplicationCloseEvent) { close() }
    }

}