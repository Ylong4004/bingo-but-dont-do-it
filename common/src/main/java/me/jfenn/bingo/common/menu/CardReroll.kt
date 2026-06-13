package me.jfenn.bingo.common.menu

import me.jfenn.bingo.common.card.CardService
import me.jfenn.bingo.common.event.model.CardShuffledEvent
import me.jfenn.bingo.common.options.OptionsService
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.utils.minus
import me.jfenn.bingo.common.utils.plus
import me.jfenn.bingo.common.utils.seconds
import me.jfenn.bingo.generated.StringKey
import me.jfenn.bingo.platform.*
import org.joml.Vector3d
import java.time.Instant
import kotlin.random.Random
import kotlin.random.nextInt

private const val REROLL_SPAM_AMOUNT = 100
private var lastReroll: Instant = Instant.MIN
private var rerolls: Int = 0

internal fun MenuComponent.registerCardReroll(
    position: Vector3d,
    width: Double,
    height: Double,
    state: BingoState = koinScope.get(),
    optionsService: OptionsService = koinScope.get(),
    cardService: CardService = koinScope.get(),
    playerManager: IPlayerManager = koinScope.get(),
    entityManager: IEntityManager = koinScope.get(),
) {
    registerTileButton(
        position = position,
        width = width,
        height = height,
        icon = "⚄",
        text = text.string(StringKey.OptionsRerollCard),
        brightness = MENU_BRIGHTNESS_ALT,
    ) { player ->
        val now = Instant.now()
        if (now - lastReroll < 1.seconds) {
            if (rerolls++ > REROLL_SPAM_AMOUNT) {
                rerolls = 0

                repeat(20) {
                    entityManager.createEntity(EntityType.TNT, player.serverWorld)
                        .also {
                            it.pos = player.pos.plus(
                                Vector3d(
                                    Random.nextDouble(-1.0, 1.0),
                                    Random.nextDouble(0.0, 2.0),
                                    Random.nextDouble(-1.0, 1.0),
                                )
                            )
                        }
                        .also { it.fuse = Random.nextInt(10..50) }
                        .also { entityManager.spawnEntity(player.serverWorld, it) }
                }
            } else {
                playerManager.playToAround(
                    player = player.player,
                    sound = PlayerSoundEvent.ENTITY_TNT_PRIMED,
                    category = PlayerSoundCategory.BLOCKS,
                    volume = rerolls / REROLL_SPAM_AMOUNT.toFloat(),
                    pitch = .5f + .5f * (rerolls / REROLL_SPAM_AMOUNT.toFloat()),
                    position = with(player.pos) {
                        Triple(x, y, z)
                    },
                    world = player.serverWorld,
                )
            }
        } else {
            rerolls = 0
        }

        // Shuffle the current card + seed
        val card = state.getActiveCard()
        cardService.shuffleCard(card)
        optionsService.broadcastHotbarMessage(player, text.string(StringKey.OptionsRerollCardSuccess))
        eventBus.emit(CardShuffledEvent, CardShuffledEvent(card.id))

        lastReroll = now
    }
}