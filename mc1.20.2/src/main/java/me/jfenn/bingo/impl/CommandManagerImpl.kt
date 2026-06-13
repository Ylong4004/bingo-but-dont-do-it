package me.jfenn.bingo.impl

import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.LongArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import com.mojang.brigadier.suggestion.SuggestionProvider
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import me.jfenn.bingo.platform.text.IText
import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.commands.*
import me.jfenn.bingo.platform.scope.BingoKoin
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.command.CommandSource
import net.minecraft.command.argument.EntityArgumentType
import net.minecraft.command.argument.MessageArgumentType
import net.minecraft.command.argument.TextArgumentType
import net.minecraft.server.MinecraftServer
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import org.koin.core.scope.Scope
import org.slf4j.Logger
import java.util.concurrent.CompletableFuture

class CommandManagerImpl(
    private val log: Logger,
) : ICommandManager {

    private val root = CommandNode.Root()

    override fun register(configure: CommandBuilder.() -> Unit) {
        CommandBuilder(root).configure()
    }

    private fun constructCommand(
        command: CommandNode,
        registryAccess: CommandRegistryAccess,
    ): ArgumentBuilder<ServerCommandSource, *> {
        var builder: ArgumentBuilder<ServerCommandSource, *> = when (command) {
            is CommandNode.Literal -> LiteralArgumentBuilder.literal(command.name)
            is CommandNode.RequiredArgument<*> -> {
                when (val arg = command.arg) {
                    is CommandArgument.String -> RequiredArgumentBuilder.argument<ServerCommandSource?, String?>(
                        arg.name,
                        if (arg.greedy) StringArgumentType.greedyString() else StringArgumentType.string()
                    ).let {
                        when (val suggestions = arg.suggestions) {
                            null -> it
                            else -> it.suggests(SuggestionProviderImpl(arg.greedy, suggestions))
                        }
                    }

                    is CommandArgument.SignedMessage -> RequiredArgumentBuilder.argument(
                        arg.name,
                        MessageArgumentType.message()
                    )

                    is CommandArgument.Text -> RequiredArgumentBuilder.argument(
                        arg.name,
                        TextArgumentType.text()
                    )

                    is CommandArgument.Bool -> RequiredArgumentBuilder.argument(
                        arg.name,
                        BoolArgumentType.bool()
                    )

                    is CommandArgument.Integer -> RequiredArgumentBuilder.argument(
                        arg.name,
                        IntegerArgumentType.integer(arg.min, arg.max)
                    )

                    is CommandArgument.NumberLong -> RequiredArgumentBuilder.argument(
                        arg.name,
                        LongArgumentType.longArg(arg.min, arg.max)
                    )

                    is CommandArgument.Player -> RequiredArgumentBuilder.argument(
                        arg.name,
                        EntityArgumentType.player()
                    )
                }
            }

            is CommandNode.Root -> throw IllegalArgumentException("Root nodes must not be provided within the tree!")
        }

        for (child in command.children) {
            val childCommand: ArgumentBuilder<ServerCommandSource, *> = constructCommand(child, registryAccess)

            @Suppress("UNCHECKED_CAST")
            builder = builder.then(childCommand) as ArgumentBuilder<ServerCommandSource, *>
        }

        if (command.requires != null) {
            builder.requires { source ->
                // Server is null when the command is being evaluated for datapack functions
                if (source.server == null)
                    return@requires command.isAvailableToDataPacks

                try {
                    command.requires?.invoke(ExecutionSourceImpl(source)) ?: true
                } catch (e: Throwable) {
                    log.error("Error in command predicate:", e)
                    false
                }
            }
        }

        if (command.callback != null) {
            builder.executes { ctx ->
                try {
                    command.callback?.invoke(ExecutionContextImpl(ctx))
                    1
                } catch (e: Throwable) {
                    if (e is CommandSyntaxException) throw e
                    log.error("Error in command handler:", e)
                    ctx.source.sendMessage(Text.literal(e.message).formatted(Formatting.RED))
                    0
                }
            }
        }

        return builder
    }

    init {
        CommandRegistrationCallback.EVENT.register { dispatcher, registryAccess, _ ->
            for (command in root.children) {
                val builder = constructCommand(command, registryAccess) as? LiteralArgumentBuilder<ServerCommandSource>
                    ?: throw IllegalArgumentException("Commands on the root node must only be literal()!")

                dispatcher.register(builder)
            }
        }
    }
}

class SuggestionProviderImpl(
    private val isGreedy: Boolean,
    private val callback: IExecutionContext.(String) -> Iterable<String>,
) : SuggestionProvider<ServerCommandSource> {
    private fun String.quoteIfNecessary(): String {
        return if (!isGreedy && contains(Regex("[^A-Za-z0-9\\-_]")))
            "\"$this\""
        else this
    }

    override fun getSuggestions(
        context: CommandContext<ServerCommandSource>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        val ctx = ExecutionContextImpl(context)
        val suggestions = callback.invoke(ctx, builder.remaining).map { it.quoteIfNecessary() }
        CommandSource.suggestMatching(suggestions, builder)
        return builder.buildFuture()
    }
}

open class ExecutionSourceImpl(
    source: ServerCommandSource,
) : IExecutionSource {
    override val server: MinecraftServer = source.server
    override val scope: Scope = BingoKoin.getScope(source.server)
        ?: throw IllegalArgumentException("No scope registered for this server!")
    override val player: IPlayerHandle? = source.player?.let { PlayerHandle(it) }
    override val isConsole: Boolean = source.entity == null
    override fun error(text: IText): Nothing {
        throw SimpleCommandExceptionType(text.value).create()
    }
}

class ExecutionContextImpl(
    private val ctx: CommandContext<ServerCommandSource>,
) : ExecutionSourceImpl(ctx.source), IExecutionContext {
    override fun sendMessage(text: IText) {
        ctx.source.sendMessage(text.value)
    }
    override fun sendFeedback(text: IText) {
        ctx.source.sendFeedback({ text.value }, true)
    }
    override fun <T> getArgument(arg: CommandArgument<T>): T {
        @Suppress("UNCHECKED_CAST")
        return when (arg) {
            is CommandArgument.String -> StringArgumentType.getString(ctx, arg.name) as T
            is CommandArgument.SignedMessage -> {
                val future = CompletableFuture<ISignedMessage>()
                MessageArgumentType.getSignedMessage(ctx, arg.name) { message ->
                    future.complete(SignedMessageImpl(message))
                }
                future as T
            }
            is CommandArgument.Text -> TextArgumentType.getTextArgument(ctx, arg.name) as T
            is CommandArgument.Bool -> BoolArgumentType.getBool(ctx, arg.name) as T
            is CommandArgument.Integer -> IntegerArgumentType.getInteger(ctx, arg.name) as T
            is CommandArgument.NumberLong -> LongArgumentType.getLong(ctx, arg.name) as T
            is CommandArgument.Player -> EntityArgumentType.getPlayer(ctx, arg.name)?.let { PlayerHandle(it) } as T
        }
    }
}
