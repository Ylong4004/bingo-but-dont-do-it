package me.jfenn.bingo.client.integrations.ddi

import me.jfenn.bingo.client.platform.IClient
import me.jfenn.bingo.client.platform.renderer.IDrawService
import me.jfenn.bingo.client.platform.screen.IButton
import me.jfenn.bingo.client.platform.screen.IButtonFactory
import me.jfenn.bingo.client.platform.screen.IKeyInput
import me.jfenn.bingo.client.platform.screen.IMutableScreenHelper
import me.jfenn.bingo.client.platform.screen.IScreen
import me.jfenn.bingo.client.platform.screen.IScreenFactory
import me.jfenn.bingo.integrations.ddi.DDIAccusationCandidateView
import me.jfenn.bingo.integrations.ddi.DDIAccusationVoteView
import me.jfenn.bingo.platform.text.ITextFactory
import net.minecraft.client.gui.screen.Screen
import net.minecraft.util.Formatting
import org.joml.Vector2i
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * 按 Y 页面中的“不要做·投票”子页面。
 *
 * 这里不保存任何投票或资格规则：它只显示服务端投影，并把按钮意图交给
 * [DDIAccusationClientActions]。因此旧数据、伪造槽位和重复投票仍由服务端拒绝。
 */
class DDIAccusationScreen(
    private val parent: Screen,
    private val client: IClient,
    private val text: ITextFactory,
    private val state: DDIAccusationClientState,
    private val actions: DDIAccusationClientActions,
    private val helper: IMutableScreenHelper,
    private val buttonFactory: IButtonFactory,
) : IScreen {

    class Factory(
        private val client: IClient,
        private val text: ITextFactory,
        private val state: DDIAccusationClientState,
        private val actions: DDIAccusationClientActions,
        private val screenFactory: IScreenFactory,
        private val buttonFactory: IButtonFactory,
    ) {
        fun create(parent: Screen): Screen = screenFactory.build(text.literal("不要做 · 举报投票")) { helper ->
            DDIAccusationScreen(parent, client, text, state, actions, helper, buttonFactory)
        }
    }

    private enum class Section { REPORT, VOTES }

    private val width by helper::width
    private val height by helper::height
    private var section = Section.REPORT
    private var page = 0
    private var pendingCandidate: DDIAccusationCandidateView? = null
    private var observedRevision = -1L

    private val closeButton = buttonFactory.createDefaultButton(
        message = text.literal("返回 Bingo"),
        onClick = { client.screen = parent },
    )

    private fun bodyLeft(): Int = max(16, width / 2 - BODY_WIDTH / 2)
    private fun bodyWidth(): Int = min(BODY_WIDTH, width - 32)

    override fun init() {
        helper.clearChildren()
        observedRevision = state.revision
        val left = bodyLeft()
        val bodyWidth = bodyWidth()

        addButton(
            message = "举报违规",
            x = left,
            y = TAB_Y,
            width = bodyWidth / 2 - 2,
            active = section != Section.REPORT,
        ) {
            section = Section.REPORT
            page = 0
            pendingCandidate = null
            init()
        }
        addButton(
            message = "进行中投票 (${state.activeVoteCount})",
            x = left + bodyWidth / 2 + 2,
            y = TAB_Y,
            width = bodyWidth / 2 - 2,
            active = section != Section.VOTES,
        ) {
            section = Section.VOTES
            page = 0
            pendingCandidate = null
            init()
        }

        closeButton.position = Vector2i(left, height - CLOSE_BUTTON_HEIGHT - 12)
        closeButton.size = Vector2i(bodyWidth, CLOSE_BUTTON_HEIGHT)
        helper.addButton(closeButton)

        when (section) {
            Section.REPORT -> initReportButtons(left, bodyWidth)
            Section.VOTES -> initVoteButtons(left, bodyWidth)
        }
    }

    private fun initReportButtons(left: Int, bodyWidth: Int) {
        val pending = pendingCandidate
        if (pending != null) {
            addButton("确认举报", left, BODY_Y + 54, bodyWidth / 2 - 2, true) {
                actions.accuse(pending)
                pendingCandidate = null
                init()
            }
            addButton("取消", left + bodyWidth / 2 + 2, BODY_Y + 54, bodyWidth / 2 - 2, true) {
                pendingCandidate = null
                init()
            }
            return
        }

        val candidates = state.candidates
        val pageCount = pageCount(candidates.size, REPORTS_PER_PAGE)
        page = page.coerceIn(0, pageCount - 1)
        candidates.drop(page * REPORTS_PER_PAGE).take(REPORTS_PER_PAGE).forEachIndexed { index, candidate ->
            addButton(
                message = "举报 ${candidate.accusedName} 的第 ${candidate.slotIndex + 1} 条词",
                x = left,
                y = BODY_Y + index * ROW_HEIGHT,
                width = bodyWidth,
                active = true,
            ) {
                pendingCandidate = candidate
                init()
            }
        }
        addPageButtons(left, bodyWidth, page, pageCount, BODY_Y + REPORTS_PER_PAGE * ROW_HEIGHT + 4)
    }

    private fun initVoteButtons(left: Int, bodyWidth: Int) {
        val votes = state.votes
        val pageCount = pageCount(votes.size, VOTES_PER_PAGE)
        page = page.coerceIn(0, pageCount - 1)
        votes.drop(page * VOTES_PER_PAGE).take(VOTES_PER_PAGE).forEachIndexed { index, vote ->
            if (!vote.canVote) return@forEachIndexed
            val y = BODY_Y + index * VOTE_HEIGHT + 42
            addButton("同意处罚", left, y, bodyWidth / 2 - 2, true) {
                actions.vote(vote.voteId, approve = true)
                init()
            }
            addButton("反对处罚", left + bodyWidth / 2 + 2, y, bodyWidth / 2 - 2, true) {
                actions.vote(vote.voteId, approve = false)
                init()
            }
        }
        addPageButtons(left, bodyWidth, page, pageCount, BODY_Y + VOTES_PER_PAGE * VOTE_HEIGHT + 4)
    }

    private fun addPageButtons(left: Int, bodyWidth: Int, page: Int, pageCount: Int, y: Int) {
        if (pageCount <= 1) return
        addButton("上一页", left, y, bodyWidth / 2 - 2, page > 0) {
            this.page--
            init()
        }
        addButton("下一页", left + bodyWidth / 2 + 2, y, bodyWidth / 2 - 2, page < pageCount - 1) {
            this.page++
            init()
        }
    }

    private fun addButton(message: String, x: Int, y: Int, width: Int, active: Boolean, onClick: () -> Unit) {
        val button: IButton = buttonFactory.createDefaultButton(text.literal(message), onClick)
        button.position = Vector2i(x, y)
        button.size = Vector2i(width, BUTTON_HEIGHT)
        button.active = active
        helper.addButton(button)
    }

    override fun render(drawService: IDrawService, mouseX: Int, mouseY: Int, delta: Float) {
        if (observedRevision != state.revision && pendingCandidate == null) init()
        val left = bodyLeft()
        val bodyWidth = bodyWidth()
        val title = text.literal("不要做挑战 · 举报投票").formatted(Formatting.GOLD, Formatting.BOLD)
        drawService.drawText(title, width / 2 - drawService.font.getTextWidth(title) / 2, 10, 0xFFFFE080.toInt(), true)

        when (section) {
            Section.REPORT -> renderReport(drawService, left, bodyWidth)
            Section.VOTES -> renderVotes(drawService, left, bodyWidth)
        }
    }

    private fun renderReport(drawService: IDrawService, left: Int, bodyWidth: Int) {
        val pending = pendingCandidate
        if (pending != null) {
            drawLine(drawService, "确认举报 ${pending.accusedName} 的第 ${pending.slotIndex + 1} 条违禁词？", left, BODY_Y + 16, Formatting.YELLOW)
            drawLine(drawService, "发起后你会自动投同意；服务端会再次校验资格。", left, BODY_Y + 34, Formatting.GRAY)
            return
        }

        val candidates = state.candidates
        if (candidates.isEmpty()) {
            drawLine(drawService, "当前没有可举报的跨队语音词条。", left, BODY_Y + 16, Formatting.GRAY)
            drawLine(drawService, "需要双方已连接语音且被举报方仍有语音词条。", left, BODY_Y + 34, Formatting.DARK_GRAY)
            return
        }
        val pageCount = pageCount(candidates.size, REPORTS_PER_PAGE)
        drawLine(drawService, "选择听到违规的玩家（${page + 1}/$pageCount 页）", left, BODY_Y - 16, Formatting.WHITE)
        drawLine(drawService, "举报后会发起最长 10 秒、2/3 通过的投票。", left, BODY_Y + REPORTS_PER_PAGE * ROW_HEIGHT + 30, Formatting.GRAY)
    }

    private fun renderVotes(drawService: IDrawService, left: Int, bodyWidth: Int) {
        val votes = state.votes
        if (votes.isEmpty()) {
            drawLine(drawService, "当前没有进行中的举报投票。", left, BODY_Y + 16, Formatting.GRAY)
            return
        }
        val pageCount = pageCount(votes.size, VOTES_PER_PAGE)
        drawLine(drawService, "投票不可更改（${page + 1}/$pageCount 页）", left, BODY_Y - 16, Formatting.WHITE)
        votes.drop(page * VOTES_PER_PAGE).take(VOTES_PER_PAGE).forEachIndexed { index, vote ->
            renderVote(drawService, vote, left, BODY_Y + index * VOTE_HEIGHT, bodyWidth)
        }
    }

    private fun renderVote(
        drawService: IDrawService,
        vote: DDIAccusationVoteView,
        left: Int,
        y: Int,
        bodyWidth: Int,
    ) {
        val seconds = ceil(vote.remainingTicks / 20.0).toInt()
        drawService.fill(left - 4, y - 4, left + bodyWidth + 4, y + VOTE_HEIGHT - 8, 0x60000000)
        drawLine(drawService, "${vote.accuserName} 举报 ${vote.accusedName} 的第 ${vote.slotIndex + 1} 条词", left, y, Formatting.YELLOW)
        drawLine(
            drawService,
            "同意 ${vote.yesVotes} · 反对 ${vote.noVotes} · 需 ${vote.approvalThreshold} 票 · 剩 ${seconds}s",
            left,
            y + 16,
            Formatting.WHITE,
        )
        when {
            vote.canVote -> drawLine(drawService, "请选择你的票：", left, y + 30, Formatting.GREEN)
            vote.ownVote > 0 -> drawLine(drawService, "你已投：同意", left, y + 30, Formatting.GREEN)
            vote.ownVote < 0 -> drawLine(drawService, "你已投：反对", left, y + 30, Formatting.RED)
            else -> drawLine(drawService, "你所在的被举报队伍无投票权。", left, y + 30, Formatting.GRAY)
        }
    }

    private fun drawLine(drawService: IDrawService, value: String, x: Int, y: Int, color: Formatting) {
        drawService.drawText(text.literal(value).formatted(color), x, y, 0xFFFFFFFF.toInt(), true)
    }

    private fun pageCount(items: Int, pageSize: Int): Int = max(1, (items + pageSize - 1) / pageSize)

    override fun keyPressed(input: IKeyInput): Boolean {
        if (input.isEscape) {
            client.screen = parent
            return true
        }
        return false
    }

    override fun shouldPause(): Boolean = false

    private companion object {
        const val BODY_WIDTH = 420
        const val TAB_Y = 30
        const val BODY_Y = 68
        const val BUTTON_HEIGHT = 20
        const val CLOSE_BUTTON_HEIGHT = 20
        const val ROW_HEIGHT = 24
        const val VOTE_HEIGHT = 72
        const val REPORTS_PER_PAGE = 6
        const val VOTES_PER_PAGE = 3
    }
}
