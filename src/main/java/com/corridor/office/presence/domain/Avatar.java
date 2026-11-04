package com.corridor.office.presence.domain;

import com.corridor.office.spaces.TileGrid.Tile;
import java.util.UUID;

/** One person on a floor, at a free position in tiles. */
public final class Avatar {

    private final UUID userId;
    private double x;
    private double y;
    private double facing;
    private boolean moving;
    private int quietTicks;
    private int stillTicks;

    Avatar(UUID userId, double x, double y) {
        this.userId = userId;
        this.x = x;
        this.y = y;
    }

    public UUID userId() {
        return userId;
    }

    public Tile tile() {
        return new Tile((int) Math.floor(x), (int) Math.floor(y));
    }

    public AvatarState state() {
        return new AvatarState(userId, x, y, facing, moving);
    }

    double x() {
        return x;
    }

    double y() {
        return y;
    }

    /** Ticks since the last accepted position, which bounds how far the next one may be. */
    int quietTicks() {
        return quietTicks;
    }

    /** Takes a position; true when anything others can see changed. */
    boolean moveTo(double toX, double toY, double toFacing, boolean walking) {
        var changed = toX != x || toY != y || toFacing != facing || walking != moving;
        x = toX;
        y = toY;
        facing = toFacing;
        moving = walking;
        quietTicks = 0;
        if (changed) {
            stillTicks = 0;
        }
        return changed;
    }

    /** One tick passed; returns how long the person has stood still, or -1 while walking. */
    int tick() {
        quietTicks = Math.min(quietTicks + 1, 10);
        return moving ? -1 : ++stillTicks;
    }
}
