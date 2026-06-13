package me.jfenn.bingo.common.menu.tooltips

import me.jfenn.bingo.common.MOD_ID_BINGO
import me.jfenn.bingo.platform.IPacketBuf
import me.jfenn.bingo.platform.packet.PacketConverter
import me.jfenn.bingo.platform.text.IText
import net.minecraft.util.Identifier
import java.time.Instant

class TooltipPacket(
    val text: List<IText>,
    val createdAt: Instant = Instant.now(),
) {

    @Transient
    var clientWrappedText: List<IText>? = null

    object V1 : PacketConverter<TooltipPacket> {
        override val id: Identifier = Identifier.of(MOD_ID_BINGO, "tooltip")!!

        override fun fromPacketBuf(buf: IPacketBuf): TooltipPacket {
            val tooltip = List(buf.readInt()) { buf.readText() }
            return TooltipPacket(tooltip)
        }

        override fun toPacketBuf(source: TooltipPacket, dest: IPacketBuf) {
            dest.writeInt(source.text.size)
            source.text.forEach { dest.writeText(it) }
        }
    }
}