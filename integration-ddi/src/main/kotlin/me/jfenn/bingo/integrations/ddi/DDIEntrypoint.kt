package me.jfenn.bingo.integrations.ddi

import me.jfenn.bingo.plugin.IBingoInternalPlugin
import org.koin.core.Koin

/**
 * DDI 模块 ServiceLoader 入口点。
 * 将 DDI Koin 模块动态加载到 Bingo 的依赖注入容器中。
 */
class DDIEntrypoint : IBingoInternalPlugin {
    override fun initialize(koin: Koin) {
        koin.loadModules(listOf(ddiModule))
    }
}
