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

        // 专用服务端的负载编解码器在整个进程内共享，必须在玩家连接前准备就绪。
        // 在集成服务端中，首个 Bingo 服务端作用域创建管理器时才会解析此单例。
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
