package me.jfenn.bingo.common.team

import me.jfenn.bingo.common.utils.measureTime
import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.IPlayerManager
import me.jfenn.bingo.platform.player.PlayerProfile
import org.slf4j.Logger

class OfflinePlayerCache(
    private val playerManager: IPlayerManager,
    private val log: Logger,
) {

    private val offlinePlayerCache = mutableMapOf<PlayerProfile, IPlayerHandle>()

    fun getOfflinePlayer(profile: PlayerProfile): IPlayerHandle {
        // If the player is online, return the current player entity
        val onlinePlayer = playerManager.getPlayer(profile.uuid)
        if (onlinePlayer != null) {
            // and set their offline cache entry
            offlinePlayerCache[profile] = onlinePlayer
            return onlinePlayer
        }

        // If the player is in the offline cache, return it
        val cachedPlayer = offlinePlayerCache[profile]
        if (cachedPlayer != null) {
            return cachedPlayer
        }

        // Otherwise, load their player data from the save (this can take a few ms...)
        val player: IPlayerHandle = log.measureTime("Loading offline player data for ${profile.name}") {
            playerManager.getOfflinePlayer(profile)
        }

        offlinePlayerCache[profile] = player
        return player
    }

}