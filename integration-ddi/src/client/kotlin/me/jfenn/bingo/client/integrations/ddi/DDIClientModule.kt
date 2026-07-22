package me.jfenn.bingo.client.integrations.ddi

import org.koin.core.module.dsl.createdAtStart
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.withOptions
import org.koin.dsl.module

/**
 * 进程级 DDI 客户端服务。
 *
 * 数据包编解码器和接收器必须在建立游戏连接前注册，因此控制器会在 Koin
 * 启动时主动创建。
 */
val ddiClientModule = module {
    singleOf(::DDIHudState)
    singleOf(::DDIAccusationClientState)
    singleOf(::DDIHudRenderer)
    singleOf(::DDIClientController) withOptions { createdAtStart() }
}
