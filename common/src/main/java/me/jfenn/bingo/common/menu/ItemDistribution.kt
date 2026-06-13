package me.jfenn.bingo.common.menu

import me.jfenn.bingo.common.card.tierlist.TierLabel
import me.jfenn.bingo.common.options.OptionsService
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.utils.plus
import me.jfenn.bingo.generated.StringKey
import me.jfenn.bingo.platform.EntityType
import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.ITextDisplayEntity
import net.minecraft.util.Formatting
import org.joml.Matrix4f
import org.joml.Vector3d

private const val TIER_PADDING = 0.1
private const val TIER_WIDTH = 0.5
private const val TIER_HEIGHT = 0.425
internal const val MENU_ITEM_DISTRIBUTION_WIDTH = TIER_WIDTH*5 + TIER_PADDING*4

internal fun MenuComponent.registerItemDistribution(
    position: Vector3d,
    state: BingoState = koinScope.get(),
    optionsService: OptionsService = koinScope.get(),
) {
    var itemDistribution by DelegatedProperty(
        getter = { state.getActiveCard().options.itemDistribution },
        setter = {},
    )

    registerTitlePanel(
        position = position + Vector3d(MENU_ITEM_DISTRIBUTION_WIDTH/2, -MENU_LINE_PADDING - MENU_LINE_HEIGHT, 0.0),
        width = MENU_ITEM_DISTRIBUTION_WIDTH,
        title = this.text.string(StringKey.OptionsCardDifficulty)
    )

    fun setItemDistribution(player: IPlayerHandle, dist: List<Int>) {
        optionsService.setCardDifficulty(
            ctx = OptionsService.Context(player),
            card = state.getActiveCard(),
            itemDistInput = dist,
            allowInvalid = true,
        )
    }

    fun createTier(
        i: Int,
        tier: TierLabel,
    ) {
        val tooltip = buildTooltip(tier.string, tier.formatting)
        val offset = Vector3d(i*(TIER_WIDTH + TIER_PADDING) + TIER_WIDTH/2, -MENU_LINE_HEIGHT - MENU_LINE_PADDING, 0.0)

        registerTileButton(
            position = position + offset.sub(0.0, MENU_LINE_PADDING + TIER_HEIGHT, 0.0),
            width = TIER_WIDTH,
            height = TIER_HEIGHT,
            text = text.literal("+"),
            tooltip = tooltip,
            isActiveProp = ConstantProperty(true),
        ) { player ->
            itemDistribution = itemDistribution.toMutableList().apply {
                set(i, (itemDistribution[i] + 1).coerceAtMost(25))
            }.also {
                setItemDistribution(player, it)
            }
        }

        val labelPosition = position + offset.sub(0.0, MENU_LINE_PADDING + TIER_HEIGHT + 0.05, 0.0) +
                Vector3d(0.0, TIER_HEIGHT/2.0 - 0.15, 0.0)
        val labelText = text.string(tier.shortString).formatted(tier.formatting, Formatting.BOLD)
        registerEntity(EntityType.TEXT_DISPLAY) {
            pos = labelPosition
            this.value = labelText
            billboard = ITextDisplayEntity.Billboard.FIXED
            alignment = ITextDisplayEntity.TextAlignment.CENTER
            background = 0
            shadow = true
        }

        val valuePosition = position + offset.sub(0.0, MENU_LINE_PADDING + TIER_HEIGHT, 0.0) +
                Vector3d(0.0, TIER_HEIGHT/2.0 + 0.05, 0.0)
        registerEntity(EntityType.TEXT_DISPLAY) {
            pos = valuePosition

            val color = when {
                itemDistribution.sum() == 25 -> Formatting.WHITE
                itemDistribution.sum() < 25 -> Formatting.YELLOW
                else -> Formatting.RED
            }

            this.value = text.literal(itemDistribution[i].toString()).formatted(color)

            billboard = ITextDisplayEntity.Billboard.FIXED
            alignment = ITextDisplayEntity.TextAlignment.CENTER
            background = 0
            shadow = true
        }

        registerTileButton(
            position = position + offset.sub(0.0, MENU_LINE_PADDING + TIER_HEIGHT, 0.0),
            width = TIER_WIDTH,
            height = TIER_HEIGHT,
            text = text.literal("-"),
            tooltip = tooltip,
            isActiveProp = ConstantProperty(true),
        ) { player ->
            itemDistribution = itemDistribution.toMutableList().apply {
                set(i, (itemDistribution[i] - 1).coerceAtLeast(0))
            }.also {
                setItemDistribution(player, it)
            }
        }
    }

    registerEntity(EntityType.BLOCK_DISPLAY) {
        pos = position + Vector3d(0.0, -4*MENU_LINE_PADDING - MENU_LINE_HEIGHT - 3*TIER_HEIGHT, -0.051)
        this.blockIdentifier = MENU_BUTTON_OFF_MATERIAL
        brightness = MENU_BRIGHTNESS_ALT
        transformation = Matrix4f().scale(MENU_ITEM_DISTRIBUTION_WIDTH.toFloat(), (2*TIER_HEIGHT + MENU_LINE_PADDING).toFloat(), 0.05f)
    }

    TierLabel.entries.forEachIndexed { i, tier -> createTier(i, tier) }
}
