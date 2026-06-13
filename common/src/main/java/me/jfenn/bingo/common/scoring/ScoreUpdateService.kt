package me.jfenn.bingo.common.scoring

import me.jfenn.bingo.common.card.BingoCard
import me.jfenn.bingo.common.card.objective.BingoObjective
import me.jfenn.bingo.common.event.packet.ServerPacketEvents
import me.jfenn.bingo.common.map.CardTileImage
import me.jfenn.bingo.common.options.BingoGoal
import me.jfenn.bingo.common.options.BingoOptions
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.team.BingoTeam
import me.jfenn.bingo.common.team.TeamCompletedCard
import me.jfenn.bingo.common.team.TeamScore
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.platform.IPlayerManager
import me.jfenn.bingo.platform.PlayerSoundCategory
import me.jfenn.bingo.platform.PlayerSoundEvent
import me.jfenn.bingo.platform.player.PlayerProfile
import java.time.Duration

internal class ScoreUpdateService(
    private val packetManager: ServerPacketEvents,
    private val options: BingoOptions,
    private val state: BingoState,
    private val text: TextProvider,
    private val playerManager: IPlayerManager,
    private val gameMessageService: GameMessageService,
) {

    fun sendItemCaptured(
        scoredPlayer: PlayerProfile?,
        team: BingoTeam,
        objective: BingoObjective,
        card: BingoCard,
    ) {
        val item = objective.display.item

        val packet = ScoredItemPacket(
            player = scoredPlayer,
            isViewerOnTeam = true,
            team = team.key,
            teamColor = team.textColor,
            score = team.currentScore.copy(items = team.currentScore.items + 1),
            itemId = item?.identifier?.toString(),
        )

        // unless showCompletedItems=false, the exact scored item should be hidden from other teams
        val otherTeamPacket = when {
            !options.showCompletedItems -> packet.copy(isViewerOnTeam = false, score = TeamScore.ZERO, itemId = null)
            card.options.isHiddenItemsMode -> packet.copy(isViewerOnTeam = false, itemId = null)
            else -> packet.copy(isViewerOnTeam = false)
        }

        // decide if all players should get notified when a different team completes an item
        val playerList = when {
            options.showCompletedItems -> playerManager.getPlayers()
            else -> team.players.mapNotNull { playerManager.getPlayer(it.uuid) }
        }

        for (player in playerList) {
            val isOnTeam = team.includesPlayer(player.player)
            val playerPacket = if (isOnTeam) packet else otherTeamPacket

            when {
                packetManager.scoredItemV1.send(player.player, playerPacket) -> {}
                else -> {
                    // play the doink sound
                    if (isOnTeam) {
                        val score = team.currentScore.items
                        player.playSound(
                            PlayerSoundEvent.ENTITY_PLAYER_LEVELUP,
                            PlayerSoundCategory.RECORDS,
                            1f, 1f + (score / 25f)
                        )
                    } else {
                        player.playSound(
                            PlayerSoundEvent.BLOCK_NOTE_BLOCK_BASS,
                            PlayerSoundCategory.RECORDS,
                            1f, 1f
                        )
                    }
                }
            }
        }

        val imageList = if (objective is BingoObjective.SomeOfEntry) {
            objective.someOfObjectives
                .mapNotNull { card.objectives[it] }
                .mapNotNull { it.display.item }
                .take(4)
                .map { CardTileImage(it, null) }
        } else emptyList()

        gameMessageService.addGameMessage(
            GameMessage.ItemScored(
                timeElapsed = state.ingameDuration() ?: Duration.ZERO,
                team = team.key,
                cardId = card.id,
                image = CardTileImage(objective.display.item, objective.display.image),
                imageList = imageList,
                decoration = objective.display.decoration,
                itemName = objective.display.name ?: text.literal("unknown"),
                player = scoredPlayer,
                isLost = false,
            )
        )
    }

    fun sendItemLost(team: BingoTeam, objective: BingoObjective, card: BingoCard) {
        val teamPlayers = team.players.mapNotNull { playerManager.getPlayer(it.uuid) }
        for (player in teamPlayers) {
            when {
                packetManager.scoredItemLostV1.send(player.player, ScoredItemLostPacket()) -> {}
                else -> {
                    player.playSound(
                        PlayerSoundEvent.ENTITY_SHULKER_AMBIENT,
                        PlayerSoundCategory.RECORDS,
                        0.8f, 1.5f
                    )
                }
            }
        }

        val imageList = if (objective is BingoObjective.SomeOfEntry) {
            objective.someOfObjectives
                .mapNotNull { card.objectives[it] }
                .mapNotNull { it.display.item }
                .take(4)
                .map { CardTileImage(it, null) }
        } else emptyList()

        gameMessageService.addGameMessage(
            GameMessage.ItemScored(
                timeElapsed = state.ingameDuration() ?: Duration.ZERO,
                team = team.key,
                cardId = card.id,
                image = CardTileImage(objective.display.item, objective.display.image),
                imageList = imageList,
                decoration = objective.display.decoration,
                itemName = objective.display.name ?: text.literal("unknown"),
                player = null,
                isLost = true,
            )
        )
    }

    fun sendLineCaptured(team: BingoTeam) {
        gameMessageService.addGameMessage(
            GameMessage.LineScored(
                timeElapsed = state.ingameDuration() ?: Duration.ZERO,
                team = team.key,
                lines = team.score.lines,
            )
        )
    }

    fun sendCardCompleted(team: BingoTeam, completedCard: TeamCompletedCard) {
        if (completedCard.isWinner) {
            gameMessageService.addGameMessage(
                GameMessage.CardCompleted(
                    timeElapsed = state.ingameDuration() ?: Duration.ZERO,
                    team = team.key,
                    isAutoWin = completedCard.isAutoWin,
                )
            )
        }

        val completedPacket = CardCompletedPacket(isWinner = completedCard.isWinner)

        playerManager.getPlayers()
            .filter { team.includesPlayer(it) }
            .forEach {
                when {
                    packetManager.cardCompletedV1.send(it, completedPacket) -> {}
                    else -> {}
                }
            }
    }

    fun sendTeamLeading(team: BingoTeam) {
        val isLinesGoal = state.getCard(team)?.options?.goal is BingoGoal.Lines
        gameMessageService.addGameMessage(
            GameMessage.LeadingTeam(
                timeElapsed = state.ingameDuration() ?: Duration.ZERO,
                team = team.key,
                cards = team.score.cards.takeIf { it > 0 },
                lines = team.score.lines.takeIf { isLinesGoal && it > 0 },
                items = team.score.items,
                isTied = false,
            )
        )
    }

    fun sendTeamTied(team: BingoTeam) {
        val isLinesGoal = state.getCard(team)?.options?.goal is BingoGoal.Lines
        gameMessageService.addGameMessage(
            GameMessage.LeadingTeam(
                timeElapsed = state.ingameDuration() ?: Duration.ZERO,
                team = team.key,
                cards = team.score.cards.takeIf { it > 0 },
                lines = team.score.lines.takeIf { isLinesGoal && it > 0 },
                items = team.score.items,
                isTied = true,
            )
        )
    }

}