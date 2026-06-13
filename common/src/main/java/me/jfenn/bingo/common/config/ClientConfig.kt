package me.jfenn.bingo.common.config

import kotlinx.serialization.Serializable
import me.jfenn.bingo.generated.StringKey

@Serializable
data class ClientConfig(
    var enableHud: Boolean = true,
    var showQuickStartButton: Boolean = true,
    var cardPausesGame: Boolean = true,
    var cardScale: Float = 1f,
    var cardAlignment: CardAlignment = CardAlignment.TOP_LEFT,
    var cardOffsetX: Int = 0,
    var cardOffsetY: Int = 0,
    var cardOverlap: CardOverlap = CardOverlap.ABOVE,
    var cardTeamOutlines: Boolean = true,
    var showMultipleCards: Boolean = false,
    var hideOnF3: Boolean = true,
    var hideOnChat: Boolean = false,
    var messageFromOtherTeams: Boolean = true,
    var messageDurationSeconds: Int = 5,
    var messageScale: Float = 1f,
    var soundVolumes: MutableMap<String, Float> = mutableMapOf(),
) {

    fun getSoundVolume(sound: String): Float {
        return soundVolumes[sound] ?: 0.8f
    }

}

enum class CardAlignment(val x: Int, val y: Int, val string: StringKey) {
    TOP_LEFT(0, 0, StringKey.ConfigCardAlignmentTopLeft),
    TOP_RIGHT(1, 0, StringKey.ConfigCardAlignmentTopRight),
    BOTTOM_LEFT(0, 1, StringKey.ConfigCardAlignmentBottomLeft),
    BOTTOM_RIGHT(1, 1, StringKey.ConfigCardAlignmentBottomRight),
}

enum class CardOverlap(val z: Int, val string: StringKey) {
    ABOVE(800, StringKey.ConfigCardOverlapAbove),
    UNDERNEATH(-800, StringKey.ConfigCardOverlapUnderneath),
}
