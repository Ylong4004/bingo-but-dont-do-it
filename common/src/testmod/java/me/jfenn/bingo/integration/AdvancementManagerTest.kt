package me.jfenn.bingo.integration

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import me.jfenn.bingo.platform.scope.BingoKoin
import me.jfenn.bingo.common.test.BaseGameTest
import me.jfenn.bingo.platform.IAdvancementManager
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest
import net.minecraft.test.GameTest
import net.minecraft.test.TestContext

class AdvancementManagerTest : BaseGameTest() {

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    fun listsAllAdvancments(context: TestContext) {
        val koin = BingoKoin.getScope(context.world.server)!!
        val advancementManager = koin.get<IAdvancementManager>()

        val advancements = advancementManager.listAdvancements(context.world.server)
        assertThat(advancements).contains("minecraft:story/mine_stone")
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    fun readsExistingAdvancement(context: TestContext) {
        val koin = BingoKoin.getScope(context.world.server)!!
        val advancementManager = koin.get<IAdvancementManager>()
        val advancement = advancementManager.getAdvancement(context.world.server, "minecraft:story/mine_stone")
        assertThat(advancement).isNotNull()
        assertThat(advancement?.displayIcon?.identifier?.toString()).isEqualTo("minecraft:wooden_pickaxe")
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    fun readsAdvancementProgress(context: TestContext) {
        val koin = BingoKoin.getScope(context.world.server)!!
        val advancementManager = koin.get<IAdvancementManager>()
        val advancement = advancementManager.getAdvancement(context.world.server, "minecraft:story/mine_stone")!!

        val player = context.createMockCreativeServerPlayerInWorld()
        val progress = advancementManager.getProgress(player, advancement)
        assertThat(progress).isEqualTo(0f)
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    fun readsAdvancementDone(context: TestContext) {
        val koin = BingoKoin.getScope(context.world.server)!!
        val advancementManager = koin.get<IAdvancementManager>()
        val advancement = advancementManager.getAdvancement(context.world.server, "minecraft:story/mine_stone")!!

        val player = context.createMockCreativeServerPlayerInWorld()
        val isDone = advancementManager.isDone(player, advancement)
        assertThat(isDone).isEqualTo(false)

        val isAnyObtained = advancementManager.isAnyObtained(player, advancement)
        assertThat(isAnyObtained).isEqualTo(false)
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    fun resetsAdvancementData(context: TestContext) {
        val koin = BingoKoin.getScope(context.world.server)!!
        val advancementManager = koin.get<IAdvancementManager>()

        val player = context.createMockCreativeServerPlayerInWorld()
        advancementManager.clearAdvancements(player)
    }

}