package me.jfenn.bingo.common.event

import me.jfenn.bingo.common.event.model.OptionsChangedEvent
import me.jfenn.bingo.common.event.model.StateChangedEvent
import me.jfenn.bingo.common.event.model.TeamChangedEvent
import me.jfenn.bingo.common.scope.BingoComponent
import me.jfenn.bingo.common.state.GameState
import me.jfenn.bingo.platform.event.IEvent
import me.jfenn.bingo.platform.event.IEventBus
import me.jfenn.bingo.platform.event.game.ScopeStopped
import me.jfenn.bingo.platform.event.model.PlayerEvent
import me.jfenn.bingo.platform.event.model.TickEvent
import me.jfenn.bingo.platform.packet.IServerPacketHandlerC2S
import me.jfenn.bingo.platform.packet.ServerPacket
import org.slf4j.Logger

class ScopedEvents(
    private val log: Logger,
    private val eventBus: IEventBus,
) : BingoComponent() {

    private object UpdateTick: IEvent<TickEvent>

    init {
        eventBus.register(TickEvent.Start) {
            if (it.ticks % 10 == 0) {
                eventBus.emit(UpdateTick, it)
            }
        }
    }

    fun onGameTick(callback: (TickEvent) -> Unit) {
        eventBus.register(TickEvent.Start, callback)
    }

    fun onUpdateTick(callback: (TickEvent) -> Unit) {
        eventBus.register(UpdateTick, callback)
    }

    /**
     * Note: this event is NOT called on the server thread!
     */
    fun onPlayerInit(callback: (PlayerEvent) -> Unit) = eventBus.register(PlayerEvent.Init, callback)
    fun onPlayerJoin(callback: (PlayerEvent) -> Unit) = eventBus.register(PlayerEvent.Join, callback)
    fun onPlayerDisconnect(callback: (PlayerEvent) -> Unit) = eventBus.register(PlayerEvent.Disconnect, callback)
    fun onPlayerRespawn(callback: (PlayerEvent) -> Unit) = eventBus.register(PlayerEvent.AfterRespawn, callback)
    fun onPlayerChannelRegister(callback: (PlayerEvent) -> Unit) {
        eventBus.register(PlayerEvent.Join, callback)
        eventBus.register(PlayerEvent.ChannelRegister, callback)
    }

    fun onStateChange(callback: (StateChangedEvent) -> Unit) = eventBus.register(StateChangedEvent, callback)
    fun onChangeOptions(callback: (Unit) -> Unit) = eventBus.register(OptionsChangedEvent, callback)
    fun onChangeTeam(callback: (TeamChangedEvent) -> Unit) {
        eventBus.register(TeamChangedEvent, callback)
    }

    fun onClose(callback: (ScopeStopped) -> Unit) = eventBus.register(ScopeStopped, callback)

    init {
        eventBus.register(StateChangedEvent) { (from, to) ->
            log.info("GameState changed: {} -> {}", from, to)
        }
    }

    fun onEnter(
        state: GameState,
        requireChange: Boolean = false,
        callback: (GameState) -> Unit,
    ) {
        eventBus.register(StateChangedEvent) { (from, to) ->
            if (to == state && (!requireChange || from != to)) {
                callback(from)
            }
        }
    }

    fun <T> onPacket(packet: IServerPacketHandlerC2S<T>, callback: (ServerPacket<T>) -> Unit) {
        eventBus.register(packet, callback)
    }
}