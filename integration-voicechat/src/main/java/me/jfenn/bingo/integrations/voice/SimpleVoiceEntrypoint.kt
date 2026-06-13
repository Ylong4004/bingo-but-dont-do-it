package me.jfenn.bingo.integrations.voice

import de.maxhenkel.voicechat.api.VoicechatPlugin
import de.maxhenkel.voicechat.api.VoicechatServerApi
import de.maxhenkel.voicechat.api.events.EventRegistration
import de.maxhenkel.voicechat.api.events.PlayerConnectedEvent
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent
import de.maxhenkel.voicechat.api.events.VoicechatServerStoppedEvent
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.ConcurrentHashMap

internal object SimpleVoiceEntrypoint : VoicechatPlugin {

    private val logger = LoggerFactory.getLogger("bingo")
    var api: VoicechatServerApi? = null
    val onPlayerConnectedGroups = ConcurrentHashMap<UUID, UUID>()

    override fun getPluginId(): String {
        return "yet-another-minecraft-bingo"
    }

    override fun registerEvents(registration: EventRegistration) {
        registration.registerEvent(VoicechatServerStartedEvent::class.java, this::onServerStarted)
        registration.registerEvent(VoicechatServerStoppedEvent::class.java, this::onServerStopped)
        registration.registerEvent(PlayerConnectedEvent::class.java, this::onPlayerConnected)
    }

    fun onServerStarted(event: VoicechatServerStartedEvent) {
        logger.debug("[SimpleVoiceEntrypoint] Voice server started")
        api = event.voicechat
    }

    fun onServerStopped(event: VoicechatServerStoppedEvent) {
        logger.debug("[SimpleVoiceEntrypoint] Voice server stopped")
        api = null
    }

    fun onPlayerConnected(event: PlayerConnectedEvent) {
        try {
            // If a player is assigned a group before they log in, we need to defer
            // the group join call until after they are connected
            // - this prevents clientside HUD de-sync
            val playerId = event.connection.player.uuid
            val groupId = onPlayerConnectedGroups.remove(event.connection.player.uuid)
            if (groupId != null) {
                SimpleVoiceApi().GroupHandle(event.voicechat, groupId)
                    .addPlayer(playerId)
            }
        } catch (e: Throwable) {
            logger.error("[SimpleVoiceEntrypoint] Error running onPlayerConnected:", e)
        }
    }
}