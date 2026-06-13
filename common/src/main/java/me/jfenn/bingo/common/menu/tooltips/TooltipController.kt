package me.jfenn.bingo.common.menu.tooltips

import me.jfenn.bingo.common.LOBBY_WORLD_IDENTIFIER
import me.jfenn.bingo.common.event.model.StateChangedEvent
import me.jfenn.bingo.common.event.packet.ServerPacketEvents
import me.jfenn.bingo.common.state.GameState
import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.IPlayerManager
import me.jfenn.bingo.platform.event.ICallbackHandle
import me.jfenn.bingo.platform.event.IEventBus
import me.jfenn.bingo.platform.event.model.TickEvent
import net.minecraft.entity.projectile.ProjectileUtil
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import org.joml.Quaternionf
import org.joml.Vector3f
import org.joml.Vector4f
import java.util.*
import kotlin.math.pow

internal class TooltipController(
    private val tooltipState: TooltipState,
    private val packets: ServerPacketEvents,
    private val playerManager: IPlayerManager,
    private val eventBus: IEventBus,
) {

    private val lookingAtTicks = mutableMapOf<Pair<UUID, UUID>, Int>()

    fun tick() {
        val prevTicks = lookingAtTicks.toMap()
        lookingAtTicks.clear()

        for (player in playerManager.getPlayers()) {
            if (player.world.identifier == LOBBY_WORLD_IDENTIFIER) {
                sendTooltip(player, prevTicks)
            }
        }
    }

    private fun sendTooltip(player: IPlayerHandle, prevTicks: Map<Pair<UUID, UUID>, Int>) {
        val reachDistance = 3.0
        val origin = with(player.player.eyePos) { Vec3d(x, y, z) }

        val pitch = Quaternionf().fromAxisAngleDeg(Vector3f(1f, 0f, 0f), -player.pitch)
        val yaw = Quaternionf().fromAxisAngleDeg(Vector3f(0f, 1f, 0f), -player.yaw)
        val direction = pitch.mul(yaw)
            .transform(Vector4f(0f, 0f, 1f, 1f))

        val vec3d3 = direction.mul(reachDistance.toFloat()) // multiply by look distance
            .let { Vec3d(it.x.toDouble(), it.y.toDouble(), it.z.toDouble()) }
            .let { origin.add(it) }

        val box = Box(
            origin.subtract(reachDistance, reachDistance, reachDistance),
            origin.add(reachDistance, reachDistance, reachDistance)
        )

        val blockDistanceSq = reachDistance.pow(2)

        val hit = ProjectileUtil.raycast(
            player.player,
            origin,
            vec3d3,
            box,
            { e -> tooltipState[e.uuid] != null },
            blockDistanceSq
        )

        val hitUuid = hit?.entity?.uuid
        val hitTooltip = hitUuid?.let { tooltipState[it] }

        if (hitUuid != null && hitTooltip != null) {
            val pair = player.uuid to hitUuid
            val ticks = prevTicks[pair] ?: 0
            lookingAtTicks[pair] = ticks + 1

            // only send tooltips once the player has been looking at the same entity for 1s
            if (ticks > 20) {
                val packet = TooltipPacket(hitTooltip)
                packets.tooltipV1.send(player.player, packet)
            }
        }
    }

    var callback: ICallbackHandle? = null

    init {
        eventBus.register(StateChangedEvent) {
            callback?.close()
            callback = if (it.to == GameState.PREGAME) {
                eventBus.register(TickEvent.Start) { tick() }
            } else {
                null
            }
        }
    }
}
