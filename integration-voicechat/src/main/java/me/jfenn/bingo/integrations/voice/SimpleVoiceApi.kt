package me.jfenn.bingo.integrations.voice

import de.maxhenkel.voicechat.api.Group
import de.maxhenkel.voicechat.api.VoicechatServerApi
import me.jfenn.bingo.platform.IPlayerHandle
import org.slf4j.LoggerFactory
import java.util.*

class SimpleVoiceApi: IVoiceApi {

    private val log = LoggerFactory.getLogger("bingo")

    override fun isInstalled(): Boolean = true

    override fun createGroup(settings: VoiceGroupSettings): IGroupHandle? {
        val serverApi = SimpleVoiceEntrypoint.api ?: return null

        // If the group is already created, return the existing instance
        serverApi.groups
            ?.find { it.name == settings.name }
            ?.let { return GroupHandle(serverApi, it.id) }

        val groupType = when (settings.type) {
            VoiceGroupType.OPEN -> Group.Type.OPEN
            VoiceGroupType.NORMAL -> Group.Type.NORMAL
            VoiceGroupType.ISOLATED -> Group.Type.ISOLATED
        }

        val group = serverApi.groupBuilder()
            ?.setPersistent(true)
            ?.setName(settings.name)
            ?.setType(groupType)
            ?.setPassword(settings.password)
            ?.setHidden(settings.hidden)
            ?.build()

        return group?.let { GroupHandle(serverApi, it.id) }
    }

    inner class GroupHandle(
        private val serverApi: VoicechatServerApi,
        private val groupId: UUID,
    ): IGroupHandle {
        override fun addPlayer(player: IPlayerHandle) {
            addPlayer(player.uuid)
        }

        fun addPlayer(player: UUID) {
            val connection = serverApi.getConnectionOf(player)
            if (connection != null && connection.isConnected) {
                val group = serverApi.getGroup(groupId)
                if (group == null) {
                    log.error("[VoiceIntegration] Cannot add player $player to group $groupId, as it does not exist!")
                }
                connection.group = group
            } else {
                log.info("[VoiceIntegration] Deferring group join for $player")
                SimpleVoiceEntrypoint.onPlayerConnectedGroups[player] = groupId
            }
        }

        override fun removePlayer(player: IPlayerHandle) {
            val connection = serverApi.getConnectionOf(player.uuid) ?: return
            if (connection.group?.id == groupId) {
                connection.group = null
            }
        }

        override fun close() {
            serverApi.removeGroup(groupId)
        }
    }
}

