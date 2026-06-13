package me.jfenn.bingo.common.test

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest
import net.minecraft.test.GameTestException
import net.minecraft.test.TestContext
import org.slf4j.LoggerFactory
import java.lang.reflect.Method

abstract class BaseGameTest : FabricGameTest {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun invokeTestMethod(context: TestContext?, method: Method?) {
        try {
            beforeEach()
            super.invokeTestMethod(context, method)
            context?.complete()
        } catch (e: Throwable) {
            logger.error(method?.name ?: "<unknown>", e)
            throw GameTestException(e.message).initCause(e)
        } finally {
            afterEach()
        }
    }

    open fun beforeEach() {}

    open fun afterEach() {}
}