package me.jfenn.bingo.integration

import assertk.assertThat
import assertk.assertions.isEqualTo
import me.jfenn.bingo.platform.IPlayerManager
import me.jfenn.bingo.platform.IStatManager
import me.jfenn.bingo.platform.scope.BingoKoin
import me.jfenn.bingo.common.test.BaseGameTest
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest
import net.minecraft.test.GameTest
import net.minecraft.test.TestContext

class StatManagerTest : BaseGameTest() {

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    fun readStatisticValue(context: TestContext) {
        val koin = BingoKoin.getScope(context.world.server)!!
        val statManager = koin.get<IStatManager>()
        val playerManager = koin.get<IPlayerManager>()

        val player = context.createMockCreativeServerPlayerInWorld()
        val playerHandle = playerManager.getPlayer(player.uuid)!!

        val stat = statManager.getById("minecraft:custom", "minecraft:bell_ring")!!
        val value = stat.getForPlayer(playerHandle)
        assertThat(value).isEqualTo(0)
    }

}