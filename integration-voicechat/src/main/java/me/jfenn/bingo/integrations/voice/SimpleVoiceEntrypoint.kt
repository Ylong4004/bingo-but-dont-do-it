package me.jfenn.bingo.integrations.voice

import de.maxhenkel.voicechat.api.VoicechatPlugin
import de.maxhenkel.voicechat.api.VoicechatServerApi
import de.maxhenkel.voicechat.api.events.EventRegistration
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent
import de.maxhenkel.voicechat.api.events.PlayerConnectedEvent
import de.maxhenkel.voicechat.api.events.PlayerDisconnectedEvent
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent
import de.maxhenkel.voicechat.api.events.VoicechatServerStoppedEvent
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.ConcurrentHashMap

internal object SimpleVoiceEntrypoint : VoicechatPlugin {

    private val logger = LoggerFactory.getLogger("bingo")
    var api: VoicechatServerApi? = null
    val onPlayerConnectedGroups = ConcurrentHashMap<UUID, UUID>()
    private var voiceKeywordBackend: SimpleVoiceKeywordBackend? = null

    override fun getPluginId(): String {
        return "yet-another-minecraft-bingo"
    }

    override fun registerEvents(registration: EventRegistration) {
        registration.registerEvent(VoicechatServerStartedEvent::class.java, this::onServerStarted)
        registration.registerEvent(VoicechatServerStoppedEvent::class.java, this::onServerStopped)
        registration.registerEvent(PlayerConnectedEvent::class.java, this::onPlayerConnected)
        registration.registerEvent(PlayerDisconnectedEvent::class.java, this::onPlayerDisconnected)
        // 进程级语音插件只注册一次；DDI 作用域本身通过 VoiceKeywordBridge
        // 内部的原子订阅进行管理。
        registration.registerEvent(MicrophonePacketEvent::class.java, this::onMicrophonePacket)
    }

    fun onServerStarted(event: VoicechatServerStartedEvent) {
        logger.debug("[SimpleVoiceEntrypoint] Voice server started")
        api = event.voicechat
        val next = SimpleVoiceKeywordBackend(
            api = event.voicechat,
            modelProvider = VoiceKeywordBridge::loadedModel,
            currentTarget = VoiceKeywordBridge::currentTarget,
            detectionSink = VoiceKeywordBridge::acceptDetection,
        )
        voiceKeywordBackend = next
        VoiceKeywordBridge.installBackend(next)
    }

    fun onServerStopped(event: VoicechatServerStoppedEvent) {
        logger.debug("[SimpleVoiceEntrypoint] Voice server stopped")
        voiceKeywordBackend?.let(VoiceKeywordBridge::uninstallBackend)
        voiceKeywordBackend = null
        api = null
    }

    fun onPlayerConnected(event: PlayerConnectedEvent) {
        try {
            // 若玩家登录前已被分配语音组，需要等连接建立后再加入该组，
            // 以避免客户端 HUD 不同步。
            val playerId = event.connection.player.uuid
            VoiceKeywordBridge.onPlayerConnected(playerId)
            val groupId = onPlayerConnectedGroups.remove(event.connection.player.uuid)
            if (groupId != null) {
                SimpleVoiceApi().GroupHandle(event.voicechat, groupId)
                    .addPlayer(playerId)
            }
        } catch (e: Throwable) {
            logger.error("[SimpleVoiceEntrypoint] Error running onPlayerConnected:", e)
        }
    }

    fun onPlayerDisconnected(event: PlayerDisconnectedEvent) {
        onPlayerConnectedGroups.remove(event.playerUuid)
        VoiceKeywordBridge.onPlayerDisconnected(event.playerUuid)
    }

    fun onMicrophonePacket(event: MicrophonePacketEvent) {
        var playerId: UUID? = null
        try {
            playerId = event.senderConnection?.player?.uuid ?: return
            VoiceKeywordBridge.recordMicrophonePacket(playerId)
            // 绝不能取消此事件：经过 O(1) 筛选并提交队列后，普通分组/近距离语音
            // 仍然必须正常广播。
            VoiceKeywordBridge.acceptMicrophonePacket(
                playerId,
                event.packet.opusEncodedData,
            )
        } catch (_: Throwable) {
            // 不记录数据包、原生库细节或任何识别内容。
            playerId?.let(VoiceKeywordBridge::recordPipelineError)
            logger.warn("[VoiceKeywords] Failed to enqueue a microphone packet")
        }
    }
}
