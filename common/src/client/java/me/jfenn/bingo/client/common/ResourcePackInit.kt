package me.jfenn.bingo.client.common

import me.jfenn.bingo.client.platform.IResourcePackManager

internal class ResourcePackInit(
    resourcePackManager: IResourcePackManager,
) {
    init {
        resourcePackManager.register("bingo:classic")
        resourcePackManager.register("bingo:futurist")
    }
}