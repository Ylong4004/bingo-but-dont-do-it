package me.jfenn.bingo.platform.commands

interface ICommandManager {
    fun register(configure: CommandBuilder.() -> Unit)

    fun register(name: String, configure: CommandBuilder.() -> Unit) {
        register {
            literal(name, configure)
        }
    }
}
