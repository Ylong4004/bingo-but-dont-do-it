package me.jfenn.bingo.integrations.ddi

import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.ClickEvent
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import org.slf4j.Logger
import java.util.UUID

data class DDIVoiceAccusationActionResult(
    val success: Boolean,
    val message: String,
)

/**
 * Minecraft 服务端对 [DDIAccusationVoteBook] 的唯一适配层。
 *
 * Interface：命令和点击消息只调用 [accuse]、[vote]、[tick] 与 [stop]。Implementation
 * 在此处冻结语音参与者、发送点击式投票提示、处理超时并要求目标管理器对旧词条二次校验。
 */
class DDIVoiceAccusationService(
    private val server: MinecraftServer,
    private val manager: DDIObjectiveManager,
    private val log: Logger,
    private val votes: DDIAccusationVoteBook = DDIAccusationVoteBook(),
) {

    /**
     * 在 DDI 语音局开始时给每个参赛者一组可点击的快捷入口。
     *
     * 玩家不需要记住授权或举报命令；命令仍保留给聊天栏翻走后的兜底使用。
     */
    fun announceRoundQuickActions() {
        server.playerManager.playerList.forEach { player ->
            val state = manager.debugVoiceState(player.uuid)
            if (!state.isActiveParticipant) return@forEach

            val message = Text.literal("§6[不要做·语音] §f本局可使用语音词条。")
            if (state.hasConsent) {
                message.append(Text.literal("§a你已同意识别。§f "))
            } else {
                message.append(
                    actionButton(
                        label = "[同意语音识别]",
                        command = "/bingoprefs ddi_voice_consent true",
                        color = Formatting.GREEN,
                    ),
                )
                message.append(Text.literal(" "))
            }
            message.append(
                actionButton(
                    label = "[举报玩家]",
                    command = "/bingo accuse",
                    color = Formatting.GOLD,
                ),
            )
            player.sendMessage(message, false)
        }
    }

    /** 显示当前可被该玩家举报的跨队语音词条，全部以点击按钮选择。 */
    fun showAccusationTargetPicker(accuser: ServerPlayerEntity) {
        val candidates = server.playerManager.playerList
            .asSequence()
            .filter { it.uuid != accuser.uuid }
            .flatMap { accused ->
                when (val preparation = manager.prepareVoiceAccusation(accuser.uuid, accused.uuid)) {
                    is DDIVoiceAccusationPreparation.Ready -> sequenceOf(
                        AccusationPickerEntry(
                            playerName = accused.name.string,
                            slotIndex = null,
                        ),
                    )
                    is DDIVoiceAccusationPreparation.AmbiguousVoiceSlots ->
                        preparation.availableSlotIndices.asSequence().map { slotIndex ->
                            AccusationPickerEntry(
                                playerName = accused.name.string,
                                slotIndex = slotIndex,
                            )
                        }
                    else -> emptySequence()
                }
            }
            .toList()

        if (candidates.isEmpty()) {
            accuser.sendMessage(
                Text.literal("§e[不要做·投票] §f当前没有可举报的跨队语音词条；" +
                    "需先同意语音识别并连接 Simple Voice Chat。"),
                false,
            )
            return
        }

        accuser.sendMessage(
            Text.literal("§6[不要做·投票] §f选择听到违规的玩家；点击后立即发起 5 秒自动结算的投票："),
            false,
        )
        candidates.chunked(PICKER_BUTTONS_PER_ROW).forEach { row ->
            val buttons = Text.empty()
            row.forEachIndexed { index, candidate ->
                if (index > 0) buttons.append(Text.literal("  "))
                buttons.append(
                    actionButton(
                        label = candidate.label,
                        command = candidate.command,
                        color = Formatting.GOLD,
                    ),
                )
            }
            accuser.sendMessage(buttons, false)
        }
    }

    fun accuse(
        accuser: ServerPlayerEntity,
        accused: ServerPlayerEntity,
        slotIndex: Int? = null,
    ): DDIVoiceAccusationActionResult {
        val preparation = manager.prepareVoiceAccusation(accuser.uuid, accused.uuid, slotIndex)
        val candidate = when (preparation) {
            is DDIVoiceAccusationPreparation.Ready -> preparation.candidate
            DDIVoiceAccusationPreparation.NoActiveRound ->
                return rejected("当前没有正在进行的不要做挑战对局。")
            DDIVoiceAccusationPreparation.AccusedNotActive ->
                return rejected("被指控玩家当前不是有效的 DDI 参与者。")
            DDIVoiceAccusationPreparation.AccusedHasNoVoiceWord ->
                return rejected("被指控玩家当前没有可用于语音指控的有效词条。")
            is DDIVoiceAccusationPreparation.AmbiguousVoiceSlots ->
                return rejected(
                    "被指控玩家有多个语音词条；请指定槽位：" +
                        preparation.availableSlotIndices.joinToString("、") { (it + 1).toString() },
                )
            DDIVoiceAccusationPreparation.AccuserNotEligible ->
                return rejected("只有被指控队伍以外、已同意且已连接语音的 DDI 玩家可以发起指控。")
        }
        val request = DDIAccusationVoteRequest(
            voteId = UUID.randomUUID(),
            accuserId = candidate.accuserId,
            accusedPlayerId = candidate.accusedPlayerId,
            accusedTeamId = candidate.accusedTeamId,
            objectiveId = candidate.objectiveId,
            slotIndex = candidate.slotIndex,
            assignmentRevision = candidate.assignmentRevision,
            eligibleVoterIds = candidate.eligibleVoterIds,
            startedAtTick = server.ticks.toLong(),
        )
        return when (val opened = votes.open(request)) {
            is DDIAccusationVoteOpenResult.Opened -> {
                broadcastVoteOpened(
                    opened.vote,
                    accuser.name.string,
                    candidate.accusedPlayerName,
                    candidate.slotIndex,
                )
                DDIVoiceAccusationActionResult(
                    success = true,
                    message = "已发起语音违规投票；你的同意票已计入，投票将在 5 秒后自动结算。",
                )
            }
            DDIAccusationVoteOpenResult.AccuserIneligible ->
                rejected("你不在这次指控冻结的合资格语音参与者中。")
            DDIAccusationVoteOpenResult.InsufficientEligibleVoters ->
                rejected("合资格投票人不足 2 名，不能处罚。")
            is DDIAccusationVoteOpenResult.AlreadyActiveForAccused ->
                rejected("该玩家已有一场未结束的指控投票。")
        }
    }

    fun vote(
        voter: ServerPlayerEntity,
        voteId: UUID,
        approve: Boolean,
    ): DDIVoiceAccusationActionResult = when (votes.cast(voteId, voter.uuid, approve)) {
        DDIAccusationVoteCastResult.ACCEPTED -> DDIVoiceAccusationActionResult(
            success = true,
            message = if (approve) "已投同意票，等待系统自动结算。" else "已投反对票，等待系统自动结算。",
        )
        DDIAccusationVoteCastResult.VOTE_NOT_FOUND -> rejected("这场投票已结束、被取消或不存在。")
        DDIAccusationVoteCastResult.VOTER_INELIGIBLE ->
            rejected("你不在这场投票创建时冻结的合资格选民中。")
        DDIAccusationVoteCastResult.ALREADY_VOTED -> rejected("你已经投过票，投票不能更改。")
    }

    /** 每个服务端游戏刻调用一次；只有到期的投票才会在此触发处罚。 */
    fun tick() {
        val resolutions = votes.resolveExpired(server.ticks.toLong())
        resolutions.forEach { resolution ->
            if (!manager.hasRound) return
            when (resolution.outcome) {
                DDIAccusationVoteOutcome.REJECTED_INSUFFICIENT_YES -> {
                    broadcast(
                        "§e[不要做·投票] §f对 ${playerName(resolution.vote.accusedPlayerId)} 的指控未通过" +
                            "（同意 ${resolution.vote.yesVoterIds.size}/${resolution.vote.approvalThreshold}）。",
                    recipients = server.playerManager.playerList,
                )
                }
                DDIAccusationVoteOutcome.APPROVED -> when (
                    manager.settleApprovedVoiceAccusation(resolution.vote)
                ) {
                    DDIVoiceAccusationSettlement.SETTLED -> broadcast(
                        "§c[不要做·投票] §f对 ${playerName(resolution.vote.accusedPlayerId)} 的指控通过，" +
                            "已执行 DDI 处罚。",
                        recipients = server.playerManager.playerList,
                    )
                    DDIVoiceAccusationSettlement.STALE_OR_INELIGIBLE -> broadcast(
                        "§e[不要做·投票] §f对 ${playerName(resolution.vote.accusedPlayerId)} 的指控已通过，" +
                            "但词条或语音资格已变化，因此不处罚。",
                        recipients = server.playerManager.playerList,
                    )
                }
            }
        }
    }

    /** 对局停止时丢弃未到期投票；结束后绝不补扣血。 */
    fun stop() {
        val cancelled = votes.cancelAll().size
        if (cancelled > 0) log.debug("[DDI Vote] Cancelled {} pending accusation vote(s)", cancelled)
    }

    private fun broadcastVoteOpened(
        vote: DDIAccusationVoteSnapshot,
        accuserName: String,
        accusedName: String,
        slotIndex: Int,
    ) {
        val headline = "§6[不要做·投票] §f$accuserName 指控 $accusedName 说出了第 ${slotIndex + 1} 条违禁词。" +
            "被指控队伍不能投票；合资格玩家请在 5 秒内表决。"
        server.playerManager.playerList.forEach { player ->
            player.sendMessage(Text.literal(headline), false)
            if (player.uuid in vote.eligibleVoterIds && player.uuid != vote.accuserId) {
                player.sendMessage(voteButtons(vote), false)
            }
        }
    }

    private fun voteButtons(vote: DDIAccusationVoteSnapshot): Text {
        val base = Text.literal("§7本票不可更改：")
        val approve = actionButton(
            label = "[同意]",
            command = "/bingo accuse vote ${vote.voteId} true",
            color = Formatting.GREEN,
        )
        val reject = actionButton(
            label = "[反对]",
            command = "/bingo accuse vote ${vote.voteId} false",
            color = Formatting.RED,
        )
        return base.append(approve).append(Text.literal(" ")).append(reject)
    }

    private fun actionButton(label: String, command: String, color: Formatting): Text =
        Text.literal(label).apply {
            setStyle(style.withColor(color).withClickEvent(ClickEvent.RunCommand(command)))
        }

    private fun playerName(playerId: UUID): String =
        server.playerManager.getPlayer(playerId)?.name?.string ?: "该玩家"

    private fun broadcast(message: String, recipients: Collection<ServerPlayerEntity>) {
        val text = Text.literal(message)
        recipients.forEach { it.sendMessage(text, false) }
    }

    private fun rejected(message: String) = DDIVoiceAccusationActionResult(false, message)

    private data class AccusationPickerEntry(
        val playerName: String,
        val slotIndex: Int?,
    ) {
        val label: String
            get() = if (slotIndex == null) {
                "[举报 $playerName]"
            } else {
                "[举报 $playerName #${slotIndex + 1}]"
            }

        val command: String
            get() = if (slotIndex == null) {
                "/bingo accuse $playerName"
            } else {
                "/bingo accuse $playerName ${slotIndex + 1}"
            }
    }

    private companion object {
        const val PICKER_BUTTONS_PER_ROW = 4
    }
}
