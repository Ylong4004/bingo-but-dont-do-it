package me.jfenn.bingo.common.text

import me.jfenn.bingo.api.services.TextPlaceholders
import me.jfenn.bingo.platform.text.IText
import net.minecraft.server.MinecraftServer

class PlaceholderService(
    private val server: MinecraftServer,
    private val text: TextProvider,
) {
    fun parseText(message: IText): IText {
        var ret = message.value
        for (service in TextPlaceholders.services)
            ret = service.parseText(ret, server)

        return text.from(ret)
    }
}