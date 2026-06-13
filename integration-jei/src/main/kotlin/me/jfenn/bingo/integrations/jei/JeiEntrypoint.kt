package me.jfenn.bingo.integrations.jei

import mezz.jei.api.IModPlugin
import mezz.jei.api.JeiPlugin
import mezz.jei.api.runtime.IJeiRuntime
import net.minecraft.util.Identifier

@JeiPlugin
internal object JeiEntrypoint : IModPlugin {

    private val IDENTIFIER = Identifier.of("yet-another-minecraft-bingo", "bingo")!!

    var runtime: IJeiRuntime? = null

    override fun getPluginUid(): Identifier = IDENTIFIER

    override fun onRuntimeAvailable(jeiRuntime: IJeiRuntime) {
        this.runtime = jeiRuntime
    }

    override fun onRuntimeUnavailable() {
        runtime = null
    }
}