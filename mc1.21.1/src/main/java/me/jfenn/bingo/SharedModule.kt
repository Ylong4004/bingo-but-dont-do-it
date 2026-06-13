package me.jfenn.bingo

import me.jfenn.bingo.common.scope.BingoScope
import me.jfenn.bingo.impl.*
import me.jfenn.bingo.impl.event.ServerCloseEvent
import me.jfenn.bingo.impl.networking.ServerNetworkingImpl
import me.jfenn.bingo.impl.particle.ParticleFactory
import me.jfenn.bingo.impl.world.GameRulesImpl
import me.jfenn.bingo.platform.*
import me.jfenn.bingo.platform.commands.ICommandManager
import me.jfenn.bingo.platform.dialog.DummyDialogManager
import me.jfenn.bingo.platform.dialog.IDialogManager
import me.jfenn.bingo.platform.item.IItemStackFactory
import me.jfenn.bingo.platform.packet.IServerNetworking
import me.jfenn.bingo.platform.particle.IParticleFactory
import me.jfenn.bingo.platform.scoreboard.IScoreboardManager
import me.jfenn.bingo.platform.text.ITextFactory
import me.jfenn.bingo.platform.world.IGameRules
import org.koin.core.module.dsl.createdAtStart
import org.koin.core.module.dsl.scopedOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.withOptions
import org.koin.dsl.bind
import org.koin.dsl.binds
import org.koin.dsl.module

val sharedServerModule = module {
    singleOf(::ServerCloseEvent) withOptions { createdAtStart() }
}

val sharedBaseModule = module {
    singleOf(::ModEnvironment) bind IModEnvironment::class
}

val sharedModule = module {
    includes(sharedBaseModule)

    singleOf(::ServerNetworkingImpl) bind IServerNetworking::class

    singleOf(::CommandManagerImpl) bind ICommandManager::class
    singleOf(::CommandRunner) bind ICommandRunner::class
    single { DummyDialogManager } bind IDialogManager::class
    singleOf(::EntityManagerImpl) bind IEntityManager::class
    single { Executors } bind IExecutors::class
    single { ItemStackFactory(get(), null) } bind IItemStackFactory::class
    singleOf(::MapColorServiceImpl) bind IMapColorService::class
    single { ParticleFactory } bind IParticleFactory::class
    singleOf(::ServerEventsImpl) withOptions { createdAtStart() }
    singleOf(::StatManagerImpl) bind IStatManager::class
    singleOf(::TagAccessorImpl) bind ITagAccessor::class
    singleOf(::TextFactoryImpl) bind ITextFactory::class
    single { JsonSerializers(get(), null) } bind IJsonSerializers::class
    singleOf(::TextSerializer) bind ITextSerializer::class

    scope<BingoScope> {
        scopedOf(::AdvancementManager) bind IAdvancementManager::class
        scopedOf(::BossBarManager) bind IBossBarManager::class
        scopedOf(::GameRulesImpl) bind IGameRules::class
        scopedOf(::PlayerManager) bind IPlayerManager::class
        scopedOf(::TeamManager) bind ITeamManager::class
        scopedOf(::MapServiceImpl) bind IMapService::class
        scopedOf(::MinecraftServerImpl) binds arrayOf(ITickManager::class, ILevelStorage::class)
        scopedOf(::MinecraftServerImpl) bind IMinecraftServer::class
        scopedOf(::ItemStackFactory) bind IItemStackFactory::class
        scopedOf(::JsonSerializers) bind IJsonSerializers::class
        scopedOf(::RecipeManagerImpl) bind IRecipeManager::class
        scopedOf(::ScoreboardManager) bind IScoreboardManager::class
        scopedOf(::ServerWorldFactory) bind IServerWorldFactory::class
        scoped { Executors.createServerTaskExecutor(get()) }
    }
}
