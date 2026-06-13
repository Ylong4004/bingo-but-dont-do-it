package me.jfenn.bingo.client.world

class BingoWorldState {
    var state: ScreenState? = null
    var isApplyingLobbyDataPack: Boolean = false
}

enum class ScreenState {
    CreateBingoWorld,
    OpenBingoWorld,
    ConfirmExperimentalFeatures,
}
