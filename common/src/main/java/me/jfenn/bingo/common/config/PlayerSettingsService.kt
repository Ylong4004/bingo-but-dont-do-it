package me.jfenn.bingo.common.config

import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.sql.BingoDatabase
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties

class PlayerSettingsService(
    private val db: BingoDatabase,
    config: BingoConfig,
) {

    private val defaultPlayerSettings = config.server.defaultPlayerSettings

    private val cache = ConcurrentHashMap<UUID, Pair<Instant, PlayerSettings>>()

    private fun clearOutdatedCache(now: Instant) {
        cache.entries
            .filter { (_, value) -> Duration.between(value.first, now) > Duration.ofMinutes(10L) }
            .map { it.key }
            .toList()
            .forEach { cache.remove(it) }
    }

    fun getPlayer(player: IPlayerHandle) = getPlayer(player.uuid)

    fun getPlayer(player: UUID): PlayerSettings {
        val now = Instant.now()
        clearOutdatedCache(now)

        val (_, cachedValue) = cache.getOrPut(player) {
            val playerSettings = db.playerSettingsQueries.findById(player.toString())
                .executeAsList()
                .associateBy { it.setting_name }

            fun getSetting(field: KProperty1<PlayerSettings, Boolean>): Boolean {
                return playerSettings[field.name]
                    ?.setting_value
                    ?.let { it > 0 }
                    ?: field.get(defaultPlayerSettings)
            }

            Instant.now() to PlayerSettings(
                seenTutorial = getSetting(PlayerSettings::seenTutorial),
                hideLobbyPrompt = getSetting(PlayerSettings::hideLobbyPrompt),
                bossbar = getSetting(PlayerSettings::bossbar),
                scoreboard = getSetting(PlayerSettings::scoreboard),
                scoreboardAutoHide = getSetting(PlayerSettings::scoreboardAutoHide),
                leadingMessages = getSetting(PlayerSettings::leadingMessages),
                scoreMessages = getSetting(PlayerSettings::scoreMessages),
                itemMessages = getSetting(PlayerSettings::itemMessages),
                nightVision = getSetting(PlayerSettings::nightVision),
            )
        }
        return cachedValue
    }

    fun write(
        player: UUID,
        field: KProperty1<PlayerSettings, *>,
        value: Boolean,
        seenTutorial: Boolean = false
    ) {
        var newValue = value

        if (field == PlayerSettings::seenTutorial) {
            newValue = value || getPlayer(player).seenTutorial
        }

        db.playerSettingsQueries.putSetting(
            me.jfenn.bingo.stats.sql.PlayerSettings(
                minecraft_id = player.toString(),
                setting_name = field.name,
                setting_value = if (newValue) 1L else 0L,
            )
        )
        if (seenTutorial) {
            db.playerSettingsQueries.putSetting(
                me.jfenn.bingo.stats.sql.PlayerSettings(
                    minecraft_id = player.toString(),
                    setting_name = PlayerSettings::seenTutorial.name,
                    setting_value = 1L,
                )
            )
        }

        cache.remove(player)
    }

    fun writeAll(player: UUID, settings: PlayerSettings) {
        PlayerSettings::class.declaredMemberProperties
            .forEach { field ->
                val value = field.get(settings)
                if (value is Boolean) {
                    write(player, field, value)
                }
            }
    }

}