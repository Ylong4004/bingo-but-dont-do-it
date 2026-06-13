package me.jfenn.bingo.integration.item

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import me.jfenn.bingo.platform.IPlayerManager
import me.jfenn.bingo.platform.item.IItemStackFactory
import me.jfenn.bingo.platform.scope.BingoKoin
import me.jfenn.bingo.common.test.BaseGameTest
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest
import net.minecraft.test.GameTest
import net.minecraft.test.TestContext
import net.minecraft.world.GameMode

class PlayerCanUseItemTest : BaseGameTest() {

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    fun canUseAppleWhenHungry(context: TestContext) {
        val koin = BingoKoin.getScope(context.world.server)!!
        val playerManager = koin.get<IPlayerManager>()
        val itemStackFactory = koin.get<IItemStackFactory>()

        val player = context.createMockCreativeServerPlayerInWorld()
        val playerImpl = playerManager.forPlayer(player)
        player.changeGameMode(GameMode.SURVIVAL)
        playerImpl.foodLevel = 0

        val itemStack = itemStackFactory.createStack("minecraft:apple", 1)
        val canUse = playerImpl.canUseItem(itemStack)
        assertThat(canUse).isTrue()
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    fun canUseAppleWhenNotHungry(context: TestContext) {
        val koin = BingoKoin.getScope(context.world.server)!!
        val playerManager = koin.get<IPlayerManager>()
        val itemStackFactory = koin.get<IItemStackFactory>()

        val player = context.createMockCreativeServerPlayerInWorld()
        val playerImpl = playerManager.forPlayer(player)
        player.changeGameMode(GameMode.SURVIVAL)
        playerImpl.foodLevel = playerImpl.maxFoodLevel

        val itemStack = itemStackFactory.createStack("minecraft:apple", 1)
        val canUse = playerImpl.canUseItem(itemStack)
        assertThat(canUse).isFalse()
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    fun canUseStewTrue(context: TestContext) {
        val koin = BingoKoin.getScope(context.world.server)!!
        val playerManager = koin.get<IPlayerManager>()
        val itemStackFactory = koin.get<IItemStackFactory>()

        val player = context.createMockCreativeServerPlayerInWorld()
        val playerImpl = playerManager.forPlayer(player)

        val itemStack = itemStackFactory.createStack("minecraft:suspicious_stew", 1)
        val canUse = playerImpl.canUseItem(itemStack)
        assertThat(canUse).isTrue()
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    fun canUseStickFalse(context: TestContext) {
        val koin = BingoKoin.getScope(context.world.server)!!
        val playerManager = koin.get<IPlayerManager>()
        val itemStackFactory = koin.get<IItemStackFactory>()

        val player = context.createMockCreativeServerPlayerInWorld()
        val playerImpl = playerManager.forPlayer(player)

        val itemStack = itemStackFactory.createStack("minecraft:stick", 1)
        val canUse = playerImpl.canUseItem(itemStack)
        assertThat(canUse).isFalse()
    }

}