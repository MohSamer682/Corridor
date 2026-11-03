package com.corridor.office.presence.domain;

import com.corridor.office.spaces.TileGrid.Tile;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

/**
 * One person on a floor. While stepping they hold two tiles, the one they stand on and the one they are
 * entering, so nobody else can step into either (the prototype's rule).
 */
public final class Avatar {

    private final UUID userId;
    private Tile tile;
    private Tile next;
    private int stepTicksLeft;
    private final Deque<Tile> path = new ArrayDeque<>();
    private int waitTicks;
    private int stillTicks;
    private char facing = 'S';

    Avatar(UUID userId, Tile tile) {
        this.userId = userId;
        this.tile = tile;
    }

    public UUID userId() {
        return userId;
    }

    public Tile tile() {
        return tile;
    }

    /** True while the person has somewhere left to go. */
    public boolean walking() {
        return next != null || !path.isEmpty();
    }

    AvatarState state() {
        var at = next != null ? next : tile;
        return new AvatarState(userId, at.x(), at.y(), facing, next != null);
    }

    /** The tile planning starts from: where the person will be once the current step ends. */
    Tile plannedFrom() {
        return next != null ? next : tile;
    }

    List<Tile> held() {
        return next == null ? List.of(tile) : List.of(tile, next);
    }

    void follow(List<Tile> route) {
        path.clear();
        path.addAll(route);
        waitTicks = 0;
    }

    /** Swaps the route while waiting, without forgetting how long we have waited. */
    void reroute(List<Tile> route) {
        path.clear();
        path.addAll(route);
    }

    Deque<Tile> path() {
        return path;
    }

    boolean stepping() {
        return next != null;
    }

    /** Counts down the current step; true when it lands on the next tile this tick. */
    boolean advanceStep() {
        if (next == null || --stepTicksLeft > 0) {
            return false;
        }
        tile = next;
        next = null;
        return true;
    }

    void startStep(Tile into, int ticks) {
        facing = facingTowards(into);
        next = into;
        stepTicksLeft = ticks;
        waitTicks = 0;
        stillTicks = 0;
    }

    int waited() {
        return ++waitTicks;
    }

    void stopWaiting() {
        path.clear();
        waitTicks = 0;
    }

    /** Counts ticks spent standing still; returns the new count. */
    int standStill() {
        return ++stillTicks;
    }

    void resetStillness() {
        stillTicks = 0;
    }

    private char facingTowards(Tile into) {
        if (into.x() > tile.x()) {
            return 'E';
        }
        if (into.x() < tile.x()) {
            return 'W';
        }
        return into.y() < tile.y() ? 'N' : 'S';
    }
}
