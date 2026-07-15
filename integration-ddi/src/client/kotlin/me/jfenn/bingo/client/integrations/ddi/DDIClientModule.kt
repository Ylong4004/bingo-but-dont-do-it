package me.jfenn.bingo.client.integrations.ddi

import org.koin.core.module.dsl.createdAtStart
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.withOptions
import org.koin.dsl.module

/**
 * Process-wide DDI client services.
 *
 * Packet codecs and receivers must be registered before a play connection is
 * established, so the controller is deliberately created when Koin starts.
 */
val ddiClientModule = module {
    singleOf(::DDIHudState)
    singleOf(::DDIHudRenderer)
    singleOf(::DDIClientController) withOptions { createdAtStart() }
}
