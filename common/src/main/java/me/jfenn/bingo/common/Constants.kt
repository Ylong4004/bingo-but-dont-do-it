package me.jfenn.bingo.common

import me.jfenn.bingo.generated.StringKey
import net.minecraft.server.MinecraftServer
import net.minecraft.util.Formatting
import net.minecraft.util.Identifier
import java.net.URI

const val MOD_ID = "bingo"

const val MOD_ID_BINGO = "yet-another-minecraft-bingo"

val LOBBY_WORLD_ID = Identifier.of("bingo", "lobby")!!
val LOBBY_WORLD_IDENTIFIER = LOBBY_WORLD_ID.toString()
val MinecraftServer.lobbyWorld get() = worlds.find { it.registryKey.value == LOBBY_WORLD_ID }

const val BINGO_TEAM_PREFIX = "bingo_"

// if true, item should be ignored when checking for bingo items
const val NBT_BINGO_IGNORE = "bingo_ignore"
// if true, item should not drop as an entity
const val NBT_BINGO_VANISH = "bingo_vanish"
// if true, player should normally be unable to remove the item from their inventory
const val NBT_BINGO_KEEP = "bingo_keep"

// indicates the bingo tutorial book item
const val NBT_BINGO_INFO_BOOK = "bingo_info_book"

const val NBT_BINGO_CARD = "bingo_card"

internal val URL_WIKI: URI = URI.create("https://horrific.dev/bingo/")
internal val URL_WIKI_WHAT_IS_BINGO: URI = URI.create("https://horrific.dev/bingo/what-is-bingo/")
internal val URL_WIKI_SERVER_SETUP: URI = URI.create("https://horrific.dev/bingo/server-setup/")
internal val URL_WIKI_GETTING_STARTED: URI = URI.create("https://horrific.dev/bingo/getting-started/")

val BINGO_WORLD_PREFIX = "${Formatting.GREEN}[BINGO]${Formatting.WHITE}"

val KEYBIND_OPEN_CARD = StringKey.KeybindOpenCard.key
val KEYBIND_TOGGLE_HUD = StringKey.KeybindToggleHud.key

const val MDC_DEBUG = "$MOD_ID_BINGO:debug"
const val MDC_FILENAME = "$MOD_ID_BINGO:filename"
const val MDC_OBJECTIVE = "$MOD_ID_BINGO:objective"
