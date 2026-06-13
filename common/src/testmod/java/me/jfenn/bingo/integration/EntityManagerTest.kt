package me.jfenn.bingo.integration

import assertk.assertThat
import assertk.assertions.*
import me.jfenn.bingo.platform.EffectType
import me.jfenn.bingo.platform.EntityType
import me.jfenn.bingo.platform.IEntityManager
import me.jfenn.bingo.platform.ITextDisplayEntity
import me.jfenn.bingo.platform.scope.BingoKoin
import me.jfenn.bingo.common.test.BaseGameTest
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest
import net.minecraft.test.GameTest
import net.minecraft.test.TestContext
import net.minecraft.text.Text

class EntityManagerTest : BaseGameTest() {

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    fun createsDisplayEntity(context: TestContext) {
        val koin = BingoKoin.getScope(context.world.server)!!
        val entityManager = koin.get<IEntityManager>()

        val entity = entityManager.createEntity(EntityType.TEXT_DISPLAY, context.world).apply {
            value = Text.literal("Hello world!")
            shadow = true
            lineWidth = 10
            background = 0x1234
            billboard = ITextDisplayEntity.Billboard.FIXED
        }
        entityManager.spawnEntity(context.world, entity)

        val foundEntity = entityManager.getEntity(context.world, entity.uuid)!! as ITextDisplayEntity
        assertThat(foundEntity.type).isEqualTo(EntityType.TEXT_DISPLAY)
        assertThat(foundEntity.value).isEqualTo(Text.literal("Hello world!"))
        assertThat(foundEntity.shadow).isTrue()
        assertThat(foundEntity.lineWidth).isEqualTo(10)
        assertThat(foundEntity.background).isEqualTo(0x1234)
        assertThat(foundEntity.billboard).isEqualTo(ITextDisplayEntity.Billboard.FIXED)

        entity.discard()
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    fun appliesCommandTags(context: TestContext) {
        val koin = BingoKoin.getScope(context.world.server)!!
        val entityManager = koin.get<IEntityManager>()

        val entity = entityManager.createEntity(EntityType.TEXT_DISPLAY, context.world)
        entityManager.spawnEntity(context.world, entity)
        entity.commandTags = setOf("test_tag")

        val foundEntity = entityManager.getEntity(context.world, entity.uuid)!!
        assertThat(foundEntity.commandTags).containsExactlyInAnyOrder("test_tag")

        entity.discard()
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    fun appliesStatusEffects(context: TestContext) {
        val koin = BingoKoin.getScope(context.world.server)!!
        val entityManager = koin.get<IEntityManager>()

        val entity = entityManager.createEntity(EntityType.BAT, context.world)
        entityManager.spawnEntity(context.world, entity)
        entity.addEffect(
            type = EffectType.INVISIBILITY,
            duration = -1,
            amplifier = 0,
            ambient = false,
            visible = false,
        )

        val effects = entity.getEffects()
        assertThat(effects).hasSize(1)
        assertThat(effects[0].type).isEqualTo(EffectType.INVISIBILITY)

        entity.removeEffect(effects[0])
        assertThat(entity.getEffects()).isEmpty()

        entity.discard()
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    fun discardsSpawnedEntity(context: TestContext) {
        val koin = BingoKoin.getScope(context.world.server)!!
        val entityManager = koin.get<IEntityManager>()

        val entity = entityManager.createEntity(EntityType.BAT, context.world)

        // when the entity is created, it does not exist in the world yet
        assertThat(
            entityManager.getEntity(context.world, entity.uuid)
        ).isNull()

        // once the entity is spawned, it should exist in the world
        entityManager.spawnEntity(context.world, entity)
        assertThat(
            entityManager.getEntity(context.world, entity.uuid)
        ).isNotNull()

        // when the entity is discarded, it is removed from the world
        entity.discard()
        assertThat(
            entityManager.getEntity(context.world, entity.uuid)
        ).isNull()
    }


}