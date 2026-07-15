package me.jfenn.bingo.integrations.ddi

import me.jfenn.bingo.platform.IModEnvironment
import me.jfenn.bingo.plugin.IBingoInternalPlugin
import org.koin.core.Koin
import org.koin.core.scope.Scope

/**
 * DDI 模块 ServiceLoader 入口点。
 * 将 DDI Koin 模块动态加载到 Bingo 的依赖注入容器中。
 */
class DDIEntrypoint : IBingoInternalPlugin {
    override fun initialize(koin: Koin) {
        koin.loadModules(listOf(ddiModule))
        koin.get<DDICommands>()

        // Dedicated-server payload codecs are process-wide and must be ready
        // before a player can connect. In an integrated server the singleton
        // is resolved when its first Bingo server scope creates the manager.
        if (koin.get<IModEnvironment>().envType == IModEnvironment.EnvType.SERVER) {
            koin.get<DDIServerPackets>()
        }
    }

    override fun onScopeStarted(scope: Scope) {
        scope.get<DDIGameController>()
    }

    override fun onScopeStopped(scope: Scope) {
        scope.get<DDIGameController>().shutdown()
    }
}
