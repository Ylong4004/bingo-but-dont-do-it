package me.jfenn.bingo.client.common.packet

import me.jfenn.bingo.client.platform.IClientNetworking
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

internal class ClientPacketEvents(
    clientNetworking: IClientNetworking,
) {

    val cardResetV1 = clientNetworking.registerS2C(CardResetPacket.V1)
    val cardDisplayV1 = clientNetworking.registerS2C(CardDisplayPacket.V1)
    val cardDisplayV2 = clientNetworking.registerS2C(CardDisplayPacket.V2)
    @Suppress("Deprecation")
    val cardUpdateV2 = clientNetworking.registerS2C(CardUpdatePacket.V2)
    @Suppress("Deprecation")
    val cardUpdateV3 = clientNetworking.registerS2C(CardUpdatePacket.V3)
    @Suppress("Deprecation")
    val cardUpdateV4 = clientNetworking.registerS2C(CardUpdatePacket.V4)
    @Suppress("Deprecation")
    val cardUpdateV5 = clientNetworking.registerS2C(CardUpdatePacket.V5)
    @Suppress("Deprecation")
    val cardUpdateV6 = clientNetworking.registerS2C(CardUpdatePacket.V6)
    val cardTilesV1 = clientNetworking.registerS2C(CardTilesPacket.V1)
    val cardTilesV2 = clientNetworking.registerS2C(CardTilesPacket.V2)
    val cardShuffledV1 = clientNetworking.registerS2C(CardShuffledPacket.V1)
    val cardCompletedV1 = clientNetworking.registerS2C(CardCompletedPacket.V1)
    val cardImageV1 = clientNetworking.registerS2C(CardImagePacket.V1)
    val gameOverV1 = clientNetworking.registerS2C(GameOverPacket.V1)
    val gameOverV2 = clientNetworking.registerS2C(GameOverPacket.V2)
    val gameOverV3 = clientNetworking.registerS2C(GameOverPacket.V3)
    val gameOverV4 = clientNetworking.registerS2C(GameOverPacket.V4)
    val gameOverV5 = clientNetworking.registerS2C(GameOverPacket.V5)
    val gameOverV6 = clientNetworking.registerS2C(GameOverPacket.V6)
    val gameOverV7 = clientNetworking.registerS2C(GameOverPacket.V7)
    val gameOverV8 = clientNetworking.registerS2C(GameOverPacket.V8)
    val scoredItemV1 = clientNetworking.registerS2C(ScoredItemPacket.V1)
    val scoredItemLostV1 = clientNetworking.registerS2C(ScoredItemLostPacket.V1)
    val scoreMessageV1 = clientNetworking.registerS2C(ScoreMessagePacket.V1)
    val scoreMessageV2 = clientNetworking.registerS2C(ScoreMessagePacket.V2)
    val scoreMessageV3 = clientNetworking.registerS2C(ScoreMessagePacket.V3)
    val gameStatusV1 = clientNetworking.registerS2C(GameStatusPacket.V1)
    val gameMessageV1 = clientNetworking.registerS2C(GameMessagePacket.V1)
    val gameMessageClearV1 = clientNetworking.registerS2C(GameMessageClearPacket.V1)
    val timerV1 = clientNetworking.registerS2C(TimerPacket.V1)
    val countdownV1 = clientNetworking.registerS2C(CountdownPacket.V1)
    val tooltipV1 = clientNetworking.registerS2C(TooltipPacket.V1)

    val readyUpdateV1 = clientNetworking.registerS2C(ReadyUpdatePacket.V1)
    val readyUpdateV2 = clientNetworking.registerS2C(ReadyUpdatePacket.V2)
    val readyUpdateV3 = clientNetworking.registerS2C(ReadyUpdatePacket.V3)
    val readySetV1 = clientNetworking.registerC2S(SetReadyPacket.V1)

    val sendPlayerSettingsV1 = clientNetworking.registerC2S(PlayerSettings.V1_C2S)
    val receivePlayerSettingsV1 = clientNetworking.registerS2C(PlayerSettings.V1_S2C)
    val sendPlayerSettingsV2 = clientNetworking.registerC2S(PlayerSettings.V2)
    val receivePlayerSettingsV2 = clientNetworking.registerS2C(PlayerSettings.V2)

    val statsHashV1C2S = clientNetworking.registerC2S(StatsCheckPacket.V1)
    val statsHashV1S2C = clientNetworking.registerS2C(StatsCheckPacket.V1)
    val statsGameV1C2S = clientNetworking.registerC2S(StatsGamePacket.V1)
    val statsGameV1S2C = clientNetworking.registerS2C(StatsGamePacket.V1)
    val statsIndexV1C2S = clientNetworking.registerC2S(StatsIndexPacket.V1)
    val statsIndexV1S2C = clientNetworking.registerS2C(StatsIndexPacket.V1)

}