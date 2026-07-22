package me.jfenn.bingo.integrations.ddi

import me.jfenn.bingo.common.options.DDIWordCatalog
import me.jfenn.bingo.common.scope.BingoScope
import org.koin.core.module.dsl.scopedOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/**
 * DDI Koin 模块 — 定义 DDI 组件的依赖注入。
 * 通过 DDIEntrypoint 的 loadModules 动态加载。
 */
val ddiModule = module {
    singleOf(::DDIServerPackets)
    singleOf(::DDICommands)

    scope<BingoScope> {
        scopedOf(::DDIWordPool)
        scoped<DDIWordCatalog> { DDIWordCatalogProvider(wordPool = get(), options = get()) }
        scopedOf(::DDITriggerDetector)
        scopedOf(::DDITabLivesService)
        scoped {
            DDIObjectiveManager(
                state = get(),
                wordPool = get(),
                triggerDetector = get(),
                packets = get(),
                tabLivesService = get(),
                historyService = get(),
                playerSettingsService = get(),
                log = get(),
            )
        }
        scopedOf(::DDISpecialEventService)
        scopedOf(::DDIVoiceKeywordController)
        scoped {
            DDIVoiceAccusationService(
                server = get(),
                manager = get(),
                packets = get(),
                log = get(),
            )
        }
        scopedOf(::DDIVoiceAccusationPacketController)
        scopedOf(::DDIGameController)
    }
}
