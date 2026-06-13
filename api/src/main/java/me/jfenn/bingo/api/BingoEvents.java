package me.jfenn.bingo.api;

import me.jfenn.bingo.api.event.EventListener;
import me.jfenn.bingo.api.event.GameEndedEvent;
import me.jfenn.bingo.api.event.GameStartedEvent;
import me.jfenn.bingo.api.event.TeamChangedEvent;

public class BingoEvents {
    public static final EventListener<IBingoApi> INIT = new EventListener<>("INIT");
    public static final EventListener<Void> CLOSE = new EventListener<>("CLOSE");
    public static final EventListener<Void> GAME_STARTING = new EventListener<>("GAME_STARTING");
    public static final EventListener<GameStartedEvent> GAME_STARTED = new EventListener<>("GAME_STARTED");
    public static final EventListener<GameEndedEvent> GAME_ENDED = new EventListener<>("GAME_ENDED");
    public static final EventListener<Void> GAME_RESET = new EventListener<>("GAME_RESET");
    public static final EventListener<TeamChangedEvent> TEAM_CHANGED = new EventListener<>("TEAM_CHANGED");
}
