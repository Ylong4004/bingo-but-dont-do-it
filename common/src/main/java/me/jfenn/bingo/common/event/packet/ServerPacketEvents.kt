package me.jfenn.bingo.common.event.packet

import me.jfenn.bingo.common.config.PlayerSettings
import me.jfenn.bingo.common.game.GameOverPacket
import me.jfenn.bingo.common.game.GameStatusPacket
import me.jfenn.bingo.common.map.*
import me.jfenn.bingo.common.menu.tooltips.TooltipPacket
import me.jfenn.bingo.common.ready.ReadyUpdatePacket
import me.jfenn.bingo.common.ready.SetReadyPacket
import me.jfenn.bingo.common.scoring.*
import me.jfenn.bingo.common.stats.packets.StatsCheckPacket
import me.jfenn.bingo.common.stats.packets.StatsGamePacket
import me.jfenn.bingo.common.stats.packets.StatsIndexPacket
import me.jfenn.bingo.common.timer.CountdownPacket
import me.jfenn.bingo.common.timer.TimerPacket
import me.jfenn.bingo.platform.packet.IServerNetworking

internal class ServerPacketEvents(
    serverNetworking: IServerNetworking,
) {

    val cardResetV1 = serverNetworking.registerS2C(CardResetPacket.V1)
    val cardDisplayV1 = serverNetworking.registerS2C(CardDisplayPacket.V1)
    val cardDisplayV2 = serverNetworking.registerS2C(CardDisplayPacket.V2)
    @Suppress("Deprecation")
    val cardUpdateV2 = serverNetworking.registerS2C(CardUpdatePacket.V2)
    @Suppress("Deprecation")
    val cardUpdateV3 = serverNetworking.registerS2C(CardUpdatePacket.V3)
    @Suppress("Deprecation")
    val cardUpdateV4 = serverNetworking.registerS2C(CardUpdatePacket.V4)
    @Suppress("Deprecation")
    val cardUpdateV5 = serverNetworking.registerS2C(CardUpdatePacket.V5)
    @Suppress("Deprecation")
    val cardUpdateV6 = serverNetworking.registerS2C(CardUpdatePacket.V6)
    val cardTilesV1 = serverNetworking.registerS2C(CardTilesPacket.V1)
    val cardTilesV2 = serverNetworking.registerS2C(CardTilesPacket.V2)
    val cardImageV1 = serverNetworking.registerS2C(CardImagePacket.V1)
    val cardShuffledV1 = serverNetworking.registerS2C(CardShuffledPacket.V1)
    val cardCompletedV1 = serverNetworking.registerS2C(CardCompletedPacket.V1)
    val gameOverV1 = serverNetworking.registerS2C(GameOverPacket.V1)
    val gameOverV2 = serverNetworking.registerS2C(GameOverPacket.V2)
    val gameOverV3 = serverNetworking.registerS2C(GameOverPacket.V3)
    val gameOverV4 = serverNetworking.registerS2C(GameOverPacket.V4)
    val gameOverV5 = serverNetworking.registerS2C(GameOverPacket.V5)
    val gameOverV6 = serverNetworking.registerS2C(GameOverPacket.V6)
    val gameOverV7 = serverNetworking.registerS2C(GameOverPacket.V7)
    val gameOverV8 = serverNetworking.registerS2C(GameOverPacket.V8)
    val scoredItemV1 = serverNetworking.registerS2C(ScoredItemPacket.V1)
    val scoredItemLostV1 = serverNetworking.registerS2C(ScoredItemLostPacket.V1)
    val scoreMessageV1 = serverNetworking.registerS2C(ScoreMessagePacket.V1)
    val scoreMessageV2 = serverNetworking.registerS2C(ScoreMessagePacket.V2)
    val scoreMessageV3 = serverNetworking.registerS2C(ScoreMessagePacket.V3)
    val gameStatusV1 = serverNetworking.registerS2C(GameStatusPacket.V1)
    val gameMessageV1 = serverNetworking.registerS2C(GameMessagePacket.V1)
    val gameMessageClearV1 = serverNetworking.registerS2C(GameMessageClearPacket.V1)
    val timerV1 = serverNetworking.registerS2C(TimerPacket.V1)
    val countdownV1 = serverNetworking.registerS2C(CountdownPacket.V1)
    val tooltipV1 = serverNetworking.registerS2C(TooltipPacket.V1)

    val readyUpdateV1 = serverNetworking.registerS2C(ReadyUpdatePacket.V1)
    val readyUpdateV2 = serverNetworking.registerS2C(ReadyUpdatePacket.V2)
    val readyUpdateV3 = serverNetworking.registerS2C(ReadyUpdatePacket.V3)
    val readySetV1 = serverNetworking.registerC2S(SetReadyPacket.V1)

    val sendPlayerSettingsV1 = serverNetworking.registerC2S(PlayerSettings.V1_C2S)
    val receivePlayerSettingsV1 = serverNetworking.registerS2C(PlayerSettings.V1_S2C)
    val sendPlayerSettingsV2 = serverNetworking.registerC2S(PlayerSettings.V2)
    val receivePlayerSettingsV2 = serverNetworking.registerS2C(PlayerSettings.V2)

    val statsHashV1C2S = serverNetworking.registerC2S(StatsCheckPacket.V1)
    val statsHashV1S2C = serverNetworking.registerS2C(StatsCheckPacket.V1)
    val statsGameV1C2S = serverNetworking.registerC2S(StatsGamePacket.V1)
    val statsGameV1S2C = serverNetworking.registerS2C(StatsGamePacket.V1)
    val statsIndexV1C2S = serverNetworking.registerC2S(StatsIndexPacket.V1)
    val statsIndexV1S2C = serverNetworking.registerS2C(StatsIndexPacket.V1)

}