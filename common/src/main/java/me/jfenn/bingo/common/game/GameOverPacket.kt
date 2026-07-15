package me.jfenn.bingo.common.game

import me.jfenn.bingo.common.MOD_ID_BINGO
import me.jfenn.bingo.common.team.BingoTeamKey
import me.jfenn.bingo.common.team.TeamScore
import me.jfenn.bingo.generated.StringKey
import me.jfenn.bingo.platform.IPacketBuf
import me.jfenn.bingo.platform.packet.PacketConverter
import me.jfenn.bingo.platform.text.IText
import net.minecraft.util.Identifier
import java.time.Duration
import java.time.Instant

data class GameOverPacket(
    val title: IText,
    val subtitle: IText,
    val winner: BingoTeamKey?,
    val duration: Duration,
    val isOperator: Boolean = false,
    val isReturnToLobbyAvailable: Boolean = false,
    val isResumeAvailable: Boolean = false,
    val isWinner: Boolean,
    /**
     * Whether the packet is sent because of a game state change;
     * - false indicates that the client should open the game over screen
     * - true indicates that the client should update its data without opening the screen
     */
    val isUpdate: Boolean,

    val winStreak: Long?,
    val bestWinStreak: Long? = null,
    val isBestWinStreak: Boolean = false,

    val capturedItems: Int? = null,
    val bestCapturedItems: Int? = null,
    val isBestCapturedItems: Boolean = false,

    val endedAt: Instant = Instant.MIN,
    val isBestTime: Boolean = false,
    val prevBestTime: Duration? = null,
    val seed: Long?,

    val scores: List<ScoreRanking> = emptyList(),
    val ddiDamageHistory: List<DDITeamDamageHistory> = emptyList(),
    val defaultTab: EndScreenTab = run {
        // If there are multiple teams & either more than one team completed the game, or the game was a draw
        if (scores.size > 1 && scores.count { it.duration != null } != 1)
            EndScreenTab.SCORES
        else EndScreenTab.CARDS
    },
) {

    enum class EndScreenTab(val title: StringKey) {
        CARDS(StringKey.GameEndTabCards),
        SCORES(StringKey.GameEndTabScores),
        DDI(StringKey.GameEndTabDdi),
    }

    class ScoreRanking(
        val index: Int,
        val key: BingoTeamKey,
        val name: IText,
        val score: TeamScore,
        val duration: Duration?
    )

    object V1 : PacketConverter<GameOverPacket> {
        override val id = Identifier.of("bingo", "game_over")!!

        override fun fromPacketBuf(buf: IPacketBuf): GameOverPacket {
            return GameOverPacket(
                title = buf.readText(),
                subtitle = buf.readText(),
                winner = if (buf.readBoolean()) BingoTeamKey(buf.readString()) else null,
                duration = buf.readDuration(),
                isOperator = buf.readBoolean(),
                isWinner = false,
                isUpdate = false,
                winStreak = null,
                seed = null,
            )
        }

        override fun toPacketBuf(source: GameOverPacket, dest: IPacketBuf) {
            dest.writeText(source.title)
            dest.writeText(source.subtitle)

            if (source.winner != null) {
                dest.writeBoolean(true)
                dest.writeString(source.winner.id)
            } else {
                dest.writeBoolean(false)
            }

            dest.writeDuration(source.duration)
            dest.writeBoolean(source.isOperator)
        }
    }

    object V2 : PacketConverter<GameOverPacket> {
        override val id = Identifier.of("bingo", "game_over_v2")!!

        override fun fromPacketBuf(buf: IPacketBuf): GameOverPacket {
            return GameOverPacket(
                title = buf.readText(),
                subtitle = buf.readText(),
                winner = if (buf.readBoolean()) BingoTeamKey(buf.readString()) else null,
                duration = buf.readDuration(),
                isOperator = buf.readBoolean(),
                isWinner = buf.readBoolean(),
                isUpdate = false,
                winStreak = null,
                seed = null,
            )
        }

        override fun toPacketBuf(source: GameOverPacket, dest: IPacketBuf) {
            dest.writeText(source.title)
            dest.writeText(source.subtitle)

            if (source.winner != null) {
                dest.writeBoolean(true)
                dest.writeString(source.winner.id)
            } else {
                dest.writeBoolean(false)
            }

            dest.writeDuration(source.duration)
            dest.writeBoolean(source.isOperator)
            dest.writeBoolean(source.isWinner)
        }
    }

    object V3 : PacketConverter<GameOverPacket> {
        override val id = Identifier.of(MOD_ID_BINGO, "game_over_v3")!!

        override fun fromPacketBuf(buf: IPacketBuf): GameOverPacket {
            return GameOverPacket(
                title = buf.readText(),
                subtitle = buf.readText(),
                winner = if (buf.readBoolean()) BingoTeamKey(buf.readString()) else null,
                duration = buf.readDuration(),
                isOperator = buf.readBoolean(),
                isWinner = buf.readBoolean(),
                isUpdate = buf.readBoolean(),
                winStreak = null,
                seed = null,
            )
        }

        override fun toPacketBuf(source: GameOverPacket, dest: IPacketBuf) {
            dest.writeText(source.title)
            dest.writeText(source.subtitle)

            if (source.winner != null) {
                dest.writeBoolean(true)
                dest.writeString(source.winner.id)
            } else {
                dest.writeBoolean(false)
            }

            dest.writeDuration(source.duration)
            dest.writeBoolean(source.isOperator)
            dest.writeBoolean(source.isWinner)
            dest.writeBoolean(source.isUpdate)
        }
    }

    object V4 : PacketConverter<GameOverPacket> {
        override val id = Identifier.of(MOD_ID_BINGO, "game_over_v4")!!

        override fun fromPacketBuf(buf: IPacketBuf): GameOverPacket {
            return GameOverPacket(
                title = buf.readText(),
                subtitle = buf.readText(),
                winner = if (buf.readBoolean()) BingoTeamKey(buf.readString()) else null,
                duration = buf.readDuration(),
                isOperator = buf.readBoolean(),
                isWinner = buf.readBoolean(),
                isUpdate = buf.readBoolean(),
                winStreak = if (buf.readBoolean()) buf.readInt().toLong() else null,
                seed = if (buf.readBoolean()) buf.readLong() else null,
            )
        }

        override fun toPacketBuf(source: GameOverPacket, dest: IPacketBuf) {
            dest.writeText(source.title)
            dest.writeText(source.subtitle)

            if (source.winner != null) {
                dest.writeBoolean(true)
                dest.writeString(source.winner.id)
            } else {
                dest.writeBoolean(false)
            }

            dest.writeDuration(source.duration)
            dest.writeBoolean(source.isOperator)
            dest.writeBoolean(source.isWinner)
            dest.writeBoolean(source.isUpdate)

            if (source.winStreak != null) {
                dest.writeBoolean(true)
                dest.writeInt(source.winStreak.toInt())
            } else {
                dest.writeBoolean(false)
            }

            if (source.seed != null) {
                dest.writeBoolean(true)
                dest.writeLong(source.seed)
            } else {
                dest.writeBoolean(false)
            }
        }
    }

    object V5 : PacketConverter<GameOverPacket> {
        override val id = Identifier.of(MOD_ID_BINGO, "game_over_v5")!!

        override fun fromPacketBuf(buf: IPacketBuf): GameOverPacket {
            return GameOverPacket(
                title = buf.readText(),
                subtitle = buf.readText(),
                winner = buf.readNullable(buf::readString)?.let { BingoTeamKey(it) },
                duration = buf.readDuration(),
                isOperator = buf.readBoolean(),
                isWinner = buf.readBoolean(),
                isUpdate = buf.readBoolean(),

                winStreak = buf.readNullable(buf::readLong),
                bestWinStreak = buf.readNullable(buf::readLong),
                isBestWinStreak = buf.readBoolean(),

                capturedItems = buf.readNullable(buf::readInt),
                bestCapturedItems = buf.readNullable(buf::readInt),
                isBestCapturedItems = buf.readBoolean(),

                endedAt = buf.readInstant(),
                isBestTime = buf.readBoolean(),
                prevBestTime = buf.readNullable(buf::readDuration),
                seed = buf.readNullable(buf::readLong),
            )
        }

        override fun toPacketBuf(source: GameOverPacket, dest: IPacketBuf) {
            dest.writeText(source.title)
            dest.writeText(source.subtitle)

            dest.writeNullable(source.winner?.id, dest::writeString)

            dest.writeDuration(source.duration)
            dest.writeBoolean(source.isOperator)
            dest.writeBoolean(source.isWinner)
            dest.writeBoolean(source.isUpdate)

            dest.writeNullable(source.winStreak, dest::writeLong)
            dest.writeNullable(source.bestWinStreak, dest::writeLong)
            dest.writeBoolean(source.isBestWinStreak)

            dest.writeNullable(source.capturedItems, dest::writeInt)
            dest.writeNullable(source.bestCapturedItems, dest::writeInt)
            dest.writeBoolean(source.isBestCapturedItems)

            dest.writeInstant(source.endedAt)
            dest.writeBoolean(source.isBestTime)
            dest.writeNullable(source.prevBestTime, dest::writeDuration)

            dest.writeNullable(source.seed, dest::writeLong)
        }
    }

    object V6 : PacketConverter<GameOverPacket> {
        override val id = Identifier.of(MOD_ID_BINGO, "game_over_v6")!!

        override fun fromPacketBuf(buf: IPacketBuf): GameOverPacket {
            return GameOverPacket(
                title = buf.readText(),
                subtitle = buf.readText(),
                winner = buf.readNullable(buf::readString)?.let { BingoTeamKey(it) },
                duration = buf.readDuration(),
                isOperator = buf.readBoolean(),
                isWinner = buf.readBoolean(),
                isUpdate = buf.readBoolean(),

                winStreak = buf.readNullable(buf::readLong),
                bestWinStreak = buf.readNullable(buf::readLong),
                isBestWinStreak = buf.readBoolean(),

                capturedItems = buf.readNullable(buf::readInt),
                bestCapturedItems = buf.readNullable(buf::readInt),
                isBestCapturedItems = buf.readBoolean(),

                endedAt = buf.readInstant(),
                isBestTime = buf.readBoolean(),
                prevBestTime = buf.readNullable(buf::readDuration),
                seed = buf.readNullable(buf::readLong),

                scores = buf.readList {
                    ScoreRanking(
                        index = buf.readInt(),
                        key = BingoTeamKey(buf.readString()),
                        name = buf.readText(),
                        score = TeamScore.V1.fromPacketBuf(buf),
                        duration = buf.readNullable(buf::readString)
                            ?.let { Duration.parse(it) }
                    )
                }
            )
        }

        override fun toPacketBuf(source: GameOverPacket, dest: IPacketBuf) {
            dest.writeText(source.title)
            dest.writeText(source.subtitle)

            dest.writeNullable(source.winner?.id, dest::writeString)

            dest.writeDuration(source.duration)
            dest.writeBoolean(source.isOperator)
            dest.writeBoolean(source.isWinner)
            dest.writeBoolean(source.isUpdate)

            dest.writeNullable(source.winStreak, dest::writeLong)
            dest.writeNullable(source.bestWinStreak, dest::writeLong)
            dest.writeBoolean(source.isBestWinStreak)

            dest.writeNullable(source.capturedItems, dest::writeInt)
            dest.writeNullable(source.bestCapturedItems, dest::writeInt)
            dest.writeBoolean(source.isBestCapturedItems)

            dest.writeInstant(source.endedAt)
            dest.writeBoolean(source.isBestTime)
            dest.writeNullable(source.prevBestTime, dest::writeDuration)

            dest.writeNullable(source.seed, dest::writeLong)

            dest.writeList(source.scores) {
                dest.writeInt(it.index)
                dest.writeString(it.key.id)
                dest.writeText(it.name)
                TeamScore.V1.toPacketBuf(it.score, dest)
                dest.writeNullable(it.duration?.toString(), dest::writeString)
            }
        }
    }

    object V7 : PacketConverter<GameOverPacket> {
        override val id = Identifier.of(MOD_ID_BINGO, "game_over_v7")!!

        override fun fromPacketBuf(buf: IPacketBuf): GameOverPacket {
            return GameOverPacket(
                title = buf.readText(),
                subtitle = buf.readText(),
                winner = buf.readNullable(buf::readString)?.let { BingoTeamKey(it) },
                duration = buf.readDuration(),
                isOperator = buf.readBoolean(),
                isWinner = buf.readBoolean(),
                isUpdate = buf.readBoolean(),

                winStreak = buf.readNullable(buf::readLong),
                bestWinStreak = buf.readNullable(buf::readLong),
                isBestWinStreak = buf.readBoolean(),

                capturedItems = buf.readNullable(buf::readInt),
                bestCapturedItems = buf.readNullable(buf::readInt),
                isBestCapturedItems = buf.readBoolean(),

                endedAt = buf.readInstant(),
                isBestTime = buf.readBoolean(),
                prevBestTime = buf.readNullable(buf::readDuration),
                seed = buf.readNullable(buf::readLong),

                scores = buf.readList {
                    ScoreRanking(
                        index = buf.readInt(),
                        key = BingoTeamKey(buf.readString()),
                        name = buf.readText(),
                        score = TeamScore.V1.fromPacketBuf(buf),
                        duration = buf.readNullable(buf::readString)
                            ?.let { Duration.parse(it) }
                    )
                },
                defaultTab = try {
                    EndScreenTab.valueOf(buf.readString())
                } catch (e: IllegalArgumentException) {
                    EndScreenTab.CARDS
                },
            )
        }

        override fun toPacketBuf(source: GameOverPacket, dest: IPacketBuf) {
            dest.writeText(source.title)
            dest.writeText(source.subtitle)

            dest.writeNullable(source.winner?.id, dest::writeString)

            dest.writeDuration(source.duration)
            dest.writeBoolean(source.isOperator)
            dest.writeBoolean(source.isWinner)
            dest.writeBoolean(source.isUpdate)

            dest.writeNullable(source.winStreak, dest::writeLong)
            dest.writeNullable(source.bestWinStreak, dest::writeLong)
            dest.writeBoolean(source.isBestWinStreak)

            dest.writeNullable(source.capturedItems, dest::writeInt)
            dest.writeNullable(source.bestCapturedItems, dest::writeInt)
            dest.writeBoolean(source.isBestCapturedItems)

            dest.writeInstant(source.endedAt)
            dest.writeBoolean(source.isBestTime)
            dest.writeNullable(source.prevBestTime, dest::writeDuration)

            dest.writeNullable(source.seed, dest::writeLong)

            dest.writeList(source.scores) {
                dest.writeInt(it.index)
                dest.writeString(it.key.id)
                dest.writeText(it.name)
                TeamScore.V1.toPacketBuf(it.score, dest)
                dest.writeNullable(it.duration?.toString(), dest::writeString)
            }
            dest.writeString(source.defaultTab.name)
        }
    }

    object V8 : PacketConverter<GameOverPacket> {
        override val id = Identifier.of(MOD_ID_BINGO, "game_over_v8")!!

        override fun fromPacketBuf(buf: IPacketBuf): GameOverPacket {
            return GameOverPacket(
                title = buf.readText(),
                subtitle = buf.readText(),
                winner = buf.readNullable(buf::readString)?.let { BingoTeamKey(it) },
                duration = buf.readDuration(),
                isReturnToLobbyAvailable = buf.readBoolean(),
                isResumeAvailable = buf.readBoolean(),
                isWinner = buf.readBoolean(),
                isUpdate = buf.readBoolean(),

                winStreak = buf.readNullable(buf::readLong),
                bestWinStreak = buf.readNullable(buf::readLong),
                isBestWinStreak = buf.readBoolean(),

                capturedItems = buf.readNullable(buf::readInt),
                bestCapturedItems = buf.readNullable(buf::readInt),
                isBestCapturedItems = buf.readBoolean(),

                endedAt = buf.readInstant(),
                isBestTime = buf.readBoolean(),
                prevBestTime = buf.readNullable(buf::readDuration),
                seed = buf.readNullable(buf::readLong),

                scores = buf.readList {
                    ScoreRanking(
                        index = buf.readInt(),
                        key = BingoTeamKey(buf.readString()),
                        name = buf.readText(),
                        score = TeamScore.V1.fromPacketBuf(buf),
                        duration = buf.readNullable(buf::readString)
                            ?.let { Duration.parse(it) }
                    )
                },
                defaultTab = try {
                    EndScreenTab.valueOf(buf.readString())
                } catch (e: IllegalArgumentException) {
                    EndScreenTab.CARDS
                },
            )
        }

        override fun toPacketBuf(source: GameOverPacket, dest: IPacketBuf) {
            dest.writeText(source.title)
            dest.writeText(source.subtitle)

            dest.writeNullable(source.winner?.id, dest::writeString)

            dest.writeDuration(source.duration)
            dest.writeBoolean(source.isReturnToLobbyAvailable)
            dest.writeBoolean(source.isResumeAvailable)
            dest.writeBoolean(source.isWinner)
            dest.writeBoolean(source.isUpdate)

            dest.writeNullable(source.winStreak, dest::writeLong)
            dest.writeNullable(source.bestWinStreak, dest::writeLong)
            dest.writeBoolean(source.isBestWinStreak)

            dest.writeNullable(source.capturedItems, dest::writeInt)
            dest.writeNullable(source.bestCapturedItems, dest::writeInt)
            dest.writeBoolean(source.isBestCapturedItems)

            dest.writeInstant(source.endedAt)
            dest.writeBoolean(source.isBestTime)
            dest.writeNullable(source.prevBestTime, dest::writeDuration)

            dest.writeNullable(source.seed, dest::writeLong)

            dest.writeList(source.scores) {
                dest.writeInt(it.index)
                dest.writeString(it.key.id)
                dest.writeText(it.name)
                TeamScore.V1.toPacketBuf(it.score, dest)
                dest.writeNullable(it.duration?.toString(), dest::writeString)
            }
            dest.writeString(source.defaultTab.name)
        }
    }

    /** Adds the per-team DDI heart-loss history shown on the game-over screen. */
    object V9 : PacketConverter<GameOverPacket> {
        override val id = Identifier.of(MOD_ID_BINGO, "game_over_v9")!!

        override fun fromPacketBuf(buf: IPacketBuf): GameOverPacket {
            return GameOverPacket(
                title = buf.readText(),
                subtitle = buf.readText(),
                winner = buf.readNullable(buf::readString)?.let { BingoTeamKey(it) },
                duration = buf.readDuration(),
                isReturnToLobbyAvailable = buf.readBoolean(),
                isResumeAvailable = buf.readBoolean(),
                isWinner = buf.readBoolean(),
                isUpdate = buf.readBoolean(),

                winStreak = buf.readNullable(buf::readLong),
                bestWinStreak = buf.readNullable(buf::readLong),
                isBestWinStreak = buf.readBoolean(),

                capturedItems = buf.readNullable(buf::readInt),
                bestCapturedItems = buf.readNullable(buf::readInt),
                isBestCapturedItems = buf.readBoolean(),

                endedAt = buf.readInstant(),
                isBestTime = buf.readBoolean(),
                prevBestTime = buf.readNullable(buf::readDuration),
                seed = buf.readNullable(buf::readLong),

                scores = buf.readList {
                    ScoreRanking(
                        index = buf.readInt(),
                        key = BingoTeamKey(buf.readString()),
                        name = buf.readText(),
                        score = TeamScore.V1.fromPacketBuf(buf),
                        duration = buf.readNullable(buf::readString)?.let(Duration::parse),
                    )
                },
                defaultTab = try {
                    EndScreenTab.valueOf(buf.readString())
                } catch (_: IllegalArgumentException) {
                    EndScreenTab.CARDS
                },
                ddiDamageHistory = buf.readList {
                    DDITeamDamageHistory(
                        teamKey = BingoTeamKey(buf.readString()),
                        teamName = buf.readString(),
                        entries = buf.readList {
                            DDIDamageHistoryEntry(
                                wordText = buf.readString(),
                                actorName = buf.readNullable(buf::readString),
                                heartsRemaining = buf.readInt(),
                                maxHearts = buf.readInt(),
                            )
                        },
                    )
                },
            )
        }

        override fun toPacketBuf(source: GameOverPacket, dest: IPacketBuf) {
            dest.writeText(source.title)
            dest.writeText(source.subtitle)
            dest.writeNullable(source.winner?.id, dest::writeString)
            dest.writeDuration(source.duration)
            dest.writeBoolean(source.isReturnToLobbyAvailable)
            dest.writeBoolean(source.isResumeAvailable)
            dest.writeBoolean(source.isWinner)
            dest.writeBoolean(source.isUpdate)

            dest.writeNullable(source.winStreak, dest::writeLong)
            dest.writeNullable(source.bestWinStreak, dest::writeLong)
            dest.writeBoolean(source.isBestWinStreak)

            dest.writeNullable(source.capturedItems, dest::writeInt)
            dest.writeNullable(source.bestCapturedItems, dest::writeInt)
            dest.writeBoolean(source.isBestCapturedItems)

            dest.writeInstant(source.endedAt)
            dest.writeBoolean(source.isBestTime)
            dest.writeNullable(source.prevBestTime, dest::writeDuration)
            dest.writeNullable(source.seed, dest::writeLong)

            dest.writeList(source.scores) {
                dest.writeInt(it.index)
                dest.writeString(it.key.id)
                dest.writeText(it.name)
                TeamScore.V1.toPacketBuf(it.score, dest)
                dest.writeNullable(it.duration?.toString(), dest::writeString)
            }
            dest.writeString(source.defaultTab.name)

            dest.writeList(source.ddiDamageHistory) { team ->
                dest.writeString(team.teamKey.id)
                dest.writeString(team.teamName)
                dest.writeList(team.entries) { entry ->
                    dest.writeString(entry.wordText)
                    dest.writeNullable(entry.actorName, dest::writeString)
                    dest.writeInt(entry.heartsRemaining)
                    dest.writeInt(entry.maxHearts)
                }
            }
        }
    }
}
