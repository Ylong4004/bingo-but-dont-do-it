package me.jfenn.bingo.integrations.voice

object DummyVoiceApi : IVoiceApi {
    override fun isInstalled(): Boolean = false
    override fun createGroup(settings: VoiceGroupSettings): IGroupHandle? = null
}