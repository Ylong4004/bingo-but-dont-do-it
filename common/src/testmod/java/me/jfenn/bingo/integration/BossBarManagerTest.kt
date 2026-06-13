package me.jfenn.bingo.integration

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.doesNotContain
import assertk.assertions.isEmpty
import me.jfenn.bingo.common.MOD_ID_BINGO
import me.jfenn.bingo.platform.scope.BingoKoin
import me.jfenn.bingo.common.test.BaseGameTest
import me.jfenn.bingo.platform.IBossBarManager
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest
import net.minecraft.test.GameTest
import net.minecraft.test.TestContext
import net.minecraft.text.Text

class BossBarManagerTest : BaseGameTest() {

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    fun listsAllBossBars(context: TestContext) {
        val koin = BingoKoin.getScope(context.world.server)!!
        val bossBarManager = koin.get<IBossBarManager>()

        bossBarManager.add("$MOD_ID_BINGO:test", Text.literal("Test Boss Bar"))

        val list = bossBarManager.list()
        assertThat(
            list.map { it.id }
        ).contains("$MOD_ID_BINGO:test")
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    fun removesABossBar(context: TestContext) {
        val koin = BingoKoin.getScope(context.world.server)!!
        val bossBarManager = koin.get<IBossBarManager>()

        val bossBar = bossBarManager.add("$MOD_ID_BINGO:test", Text.literal("Test Boss Bar"))
        bossBarManager.remove(bossBar)

        assertThat(bossBarManager.list()).doesNotContain("$MOD_ID_BINGO:test")
    }

}