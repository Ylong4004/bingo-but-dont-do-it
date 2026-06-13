package me.jfenn.bingo.common.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import me.jfenn.bingo.platform.IPacketBuf
import me.jfenn.bingo.platform.packet.PacketConverter
import me.jfenn.bingo.common.utils.json
import net.minecraft.util.Identifier

@Serializable
data class PlayerSettings(
    val seenTutorial: Boolean = false,
    val hideLobbyPrompt: Boolean = false,
    val bossbar: Boolean = false,
    val scoreboard: Boolean = true,
    val scoreboardAutoHide: Boolean = false,
    val leadingMessages: Boolean = true,
    val scoreMessages: Boolean = true,
    val itemMessages: Boolean = scoreMessages,
    val nightVision: Boolean = true,
) {
    object V1_S2C : PacketConverter<PlayerSettings> {
        override val id: Identifier = Identifier.of("bingo", "update_settings_s2c")!!

        override fun fromPacketBuf(buf: IPacketBuf): PlayerSettings {
            buf.readInt()
            return PlayerSettings(
                seenTutorial = buf.readBoolean(),
                bossbar = buf.readBoolean(),
                scoreboard = buf.readBoolean(),
                scoreboardAutoHide = buf.readBoolean(),
                leadingMessages = buf.readBoolean(),
                scoreMessages = buf.readBoolean(),
                nightVision = buf.readBoolean(),
            )
        }

        override fun toPacketBuf(source: PlayerSettings, dest: IPacketBuf) {
            dest.writeInt(0)
            dest.writeBoolean(source.seenTutorial)
            dest.writeBoolean(source.bossbar)
            dest.writeBoolean(source.scoreboard)
            dest.writeBoolean(source.scoreboardAutoHide)
            dest.writeBoolean(source.leadingMessages)
            dest.writeBoolean(source.scoreMessages)
            dest.writeBoolean(source.nightVision)
        }
    }

    object V1_C2S : PacketConverter<PlayerSettings> {
        override val id: Identifier = Identifier.of("bingo", "update_settings_c2s")!!
        override fun fromPacketBuf(buf: IPacketBuf) = V1_S2C.fromPacketBuf(buf)
        override fun toPacketBuf(source: PlayerSettings, dest: IPacketBuf) = V1_S2C.toPacketBuf(source, dest)
    }

    object V2 : PacketConverter<PlayerSettings> {
        override val id: Identifier = Identifier.of("bingo", "update_settings_v2")!!

        override fun fromPacketBuf(buf: IPacketBuf): PlayerSettings {
            val str = buf.readString()
            return json.decodeFromString(str)
        }

        override fun toPacketBuf(source: PlayerSettings, dest: IPacketBuf) {
            val str = json.encodeToString(source)
            dest.writeString(str)
        }
    }
}
