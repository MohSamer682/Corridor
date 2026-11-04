package com.corridor.office.presence.domain;

import com.corridor.office.presence.domain.MovementIntent.Enter;
import com.corridor.office.presence.domain.MovementIntent.Leave;
import com.corridor.office.presence.domain.MovementIntent.Move;
import com.corridor.office.spaces.TileGrid;
import com.corridor.office.spaces.TileGrid.Tile;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Everyone on one floor. Browsers walk their own avatar freely, like the prototype, and report where it is; the
 * floor accepts a position only if it is inside walkable floor, reachable at walking speed, and not on top of
 * someone else. Anything else is refused and the person is put back. Intents may arrive on any thread; only
 * {@link #tick()} changes positions, and one thread runs the ticks of a floor.
 */
public final class FloorSession {

    /** Fastest a browser may walk someone, in tiles per second; the client walks at about 6. */
    public static final double MAX_SPEED = 7.5;
    /** How close two people may stand, centre to centre, in tiles (the prototype's 22 px at 32 px a tile). */
    public static final double MIN_GAP = 0.65;
    /** Half the width of a person, for keeping clear of walls. */
    public static final double RADIUS = 0.28;
    /** Still for 3 s: count as sitting where you stand. */
    public static final int SETTLE_TICKS = 30;

    private static final double TICK_SECONDS = 0.1;
    private static final double SLACK = 0.3;

    private final TileGrid grid;
    private final Map<UUID, Avatar> avatars = new LinkedHashMap<>();
    private final ConcurrentLinkedQueue<MovementIntent> intents = new ConcurrentLinkedQueue<>();

    public FloorSession(TileGrid grid) {
        this.grid = grid;
    }

    public void submit(MovementIntent intent) {
        intents.add(intent);
    }

    public Frame tick() {
        var moved = new LinkedHashMap<UUID, AvatarState>();
        var corrected = new LinkedHashMap<UUID, AvatarState>();
        var left = new ArrayList<UUID>();
        var settled = new ArrayList<Settled>();
        for (var avatar : avatars.values()) {
            if (avatar.tick() == SETTLE_TICKS) {
                settled.add(new Settled(avatar.userId(), avatar.tile()));
            }
        }
        for (MovementIntent intent; (intent = intents.poll()) != null; ) {
            switch (intent) {
                case Enter enter -> enter(enter.userId(), moved);
                case Move move -> move(move, moved, corrected);
                case Leave leave -> {
                    if (avatars.remove(leave.userId()) != null) {
                        moved.remove(leave.userId());
                        corrected.remove(leave.userId());
                        left.add(leave.userId());
                    }
                }
            }
        }
        return new Frame(
                List.copyOf(moved.values()), List.copyOf(left), List.copyOf(settled), List.copyOf(corrected.values()));
    }

    public Optional<Avatar> avatar(UUID userId) {
        return Optional.ofNullable(avatars.get(userId));
    }

    public List<AvatarState> everyone() {
        return avatars.values().stream().map(Avatar::state).toList();
    }

    public boolean isEmpty() {
        return avatars.isEmpty() && intents.isEmpty();
    }

    private void enter(UUID userId, Map<UUID, AvatarState> moved) {
        if (avatars.containsKey(userId)) {
            return;
        }
        grid.nearestFree(grid.arrival(), crowdedTiles()).ifPresent(tile -> {
            var entered = new Avatar(userId, tile.x() + 0.5, tile.y() + 0.5);
            avatars.put(userId, entered);
            moved.put(userId, entered.state());
        });
    }

    private void move(Move move, Map<UUID, AvatarState> moved, Map<UUID, AvatarState> corrected) {
        var avatar = avatars.get(move.userId());
        if (avatar == null) {
            return;
        }
        if (!possible(avatar, move.x(), move.y())) {
            corrected.put(avatar.userId(), avatar.state());
            return;
        }
        corrected.remove(avatar.userId());
        if (avatar.moveTo(move.x(), move.y(), move.facing(), move.moving())) {
            moved.put(avatar.userId(), avatar.state());
        }
    }

    private boolean possible(Avatar avatar, double x, double y) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !clear(x, y)) {
            return false;
        }
        var step = Math.hypot(x - avatar.x(), y - avatar.y());
        var reach = MAX_SPEED * TICK_SECONDS * Math.max(1, avatar.quietTicks()) + SLACK;
        return step <= reach && clearLine(avatar.x(), avatar.y(), x, y) && !crowds(avatar, x, y);
    }

    // The person's footprint, a square of 2 * RADIUS, stands on walkable floor only.
    private boolean clear(double x, double y) {
        return walkable(x - RADIUS, y - RADIUS)
                && walkable(x + RADIUS, y - RADIUS)
                && walkable(x - RADIUS, y + RADIUS)
                && walkable(x + RADIUS, y + RADIUS);
    }

    private boolean walkable(double x, double y) {
        return grid.isWalkable(new Tile((int) Math.floor(x), (int) Math.floor(y)));
    }

    // No cutting through a wall corner between two good positions.
    private boolean clearLine(double fromX, double fromY, double toX, double toY) {
        var steps = (int) Math.ceil(Math.hypot(toX - fromX, toY - fromY) / 0.25);
        for (int i = 1; i < steps; i++) {
            var t = (double) i / steps;
            if (!walkable(fromX + (toX - fromX) * t, fromY + (toY - fromY) * t)) {
                return false;
            }
        }
        return true;
    }

    // Too close to someone, and getting closer. Stepping away from someone already close is fine.
    private boolean crowds(Avatar mover, double x, double y) {
        for (var other : avatars.values()) {
            if (other == mover) {
                continue;
            }
            var after = Math.hypot(other.x() - x, other.y() - y);
            if (after < MIN_GAP && after < Math.hypot(other.x() - mover.x(), other.y() - mover.y())) {
                return true;
            }
        }
        return false;
    }

    // Tiles whose centre is too close to someone, so a newcomer never lands on top of anyone.
    private Set<Tile> crowdedTiles() {
        var taken = new HashSet<Tile>();
        for (var other : avatars.values()) {
            var at = other.tile();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    var tile = new Tile(at.x() + dx, at.y() + dy);
                    if (Math.hypot(tile.x() + 0.5 - other.x(), tile.y() + 0.5 - other.y()) < MIN_GAP) {
                        taken.add(tile);
                    }
                }
            }
        }
        return taken;
    }
}
