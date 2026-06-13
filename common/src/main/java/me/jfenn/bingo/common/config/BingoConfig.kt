package me.jfenn.bingo.common.config

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import me.jfenn.bingo.common.card.filter.ObjectiveFilterList
import me.jfenn.bingo.common.card.tierlist.TierLabel
import me.jfenn.bingo.platform.utils.UuidAsString
import java.util.*

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class BingoConfig(
    var version: Int = 0,

    var itemFilterPresets: Map<String, ObjectiveFilterList> = ObjectiveFilterList.PRESETS,
    var difficultyPresets: Map<String, List<Int>> = TierLabel.DIFFICULTY_PRESETS,

    // adds BINGO_IGNORE to spawn kit items, preventing them from being scored
    val preventScoringSpawnKitItems: Boolean = false,
    // excludes the active spawn kit items from item tiers when generating a card
    val excludeSpawnKitItemsFromCards: Boolean = true,

    // stops sounds from being sent to other players in the lobby
    val preventLobbyChaos: Boolean = false,
    // gives players a tutorial/settings book when they join the lobby
    val lobbyTutorialBook: Boolean = true,

    // gives players a "memento" card after the game ends (if isLobbyMode=false)
    val giveMementoInSurvival: Boolean = true,

    val nightVisionInSpectator: Boolean = true,
    val nightVisionInPostgame: Boolean = true,

    // whether the server should allow connecting clients to use the bingo HUD
    // instead of the vanilla map/card behavior
    var supportClientHud: Boolean = true,

    // starts the game after the time limit - reduced when players vote /ready
    val startWhenReadySeconds: Int? = 600,
    val startWhenReadyWaitsForTeams: Boolean = true,
    val startWhenReadyWaitsForFirstVote: Boolean = false,
    // resets after a time limit - reduced when players vote /ready
    val nextRoundWhenReadySeconds: Int? = 600,
    val nextRoundWhenReadyWaitsForFirstVote: Boolean = true,

    // amount of time to wait before starting the game
    val countdownDelayTicks: Int = 100,
    val countdownSeconds: Int = 3,

    // resets the server once all players log out after a game
    @JsonNames("autoShutDownInPostgame", "shutDownWhenEveryoneDisconnects")
    val nextRoundWhenEveryoneDisconnects: Boolean = true,
    // shuts down the server faster, at the risk of bugs or data corruption
    val unsafeSkipWorldClose: Boolean = false,

    val chat: ChatConfig = ChatConfig(),

    val databaseUrl: String? = null,
    val databaseUser: String? = null,
    val databasePass: String? = null,

    // whether to sync game stats with any connected servers/clients
    var syncStats: Boolean = true,
    // the "host_id" stored in stats.db to distinguish games synced vs. played on a server
    val statsHostId: UuidAsString = UUID.randomUUID(),

    // only applies to client
    val client: ClientConfig = ClientConfig(),

    // only applies to dedicated server
    val server: ServerConfig = ServerConfig(),
)
