package me.jfenn.bingo.client.integrations.ddi

import me.jfenn.bingo.platform.text.ITextFactory
import me.jfenn.bingo.client.common.hud.screen.BingoHudScreenAction
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
    singleOf(::DDIAccusationClientActions)
    single {
        DDIAccusationScreen.Factory(
            client = get(),
            text = get(),
            state = get(),
            actions = get(),
            screenFactory = get(),
            buttonFactory = get(),
        )
    }
    single<BingoHudScreenAction> {
        DDIAccusationScreenAction(
            hudState = get(),
            accusationState = get(),
            screenFactory = get(),
            label = get<ITextFactory>().literal("不要做 · 投票"),
        )
    }
    singleOf(::DDIHudRenderer)
    singleOf(::DDIClientController) withOptions { createdAtStart() }
}
