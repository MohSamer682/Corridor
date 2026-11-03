package com.corridor.office.presence.domain;

import com.corridor.office.presence.domain.MovementIntent.Enter;
import com.corridor.office.presence.domain.MovementIntent.Leave;
import com.corridor.office.presence.domain.MovementIntent.Step;
import com.corridor.office.presence.domain.MovementIntent.WalkTo;
import com.corridor.office.spaces.TileGrid;
import com.corridor.office.spaces.TileGrid.Tile;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Everyone on one floor. Intents may arrive on any thread; only {@link #tick()} changes positions, and one
 * thread runs the ticks of a floor, so the tile rules need no locks.
 */
public final class FloorSession {

    /** A tile takes two ticks of 100 ms: five tiles a second. */
    public static final int STEP_TICKS = 2;
    /** Blocked for 0.4 s: look for a way around the person in front. */
    public static final int REPATH_TICKS = 4;
    /** Blocked for 2.5 s: stop and stay put. */
    public static final int GIVE_UP_TICKS = 25;
    /** Still for 3 s: count as sitting where you stand. */
    public static final int SETTLE_TICKS = 30;

    private final TileGrid grid;
    private final Map<UUID, Avatar> avatars = new LinkedHashMap<>();
    private final ConcurrentLinkedQueue<MovementIntent> intents = new ConcurrentLinkedQueue<>();
    // Who holds each tile, kept in step with the avatars so a tick doesn't rebuild it per person.
    private final Map<Tile, Avatar> holders = new HashMap<>();

    public FloorSession(TileGrid grid) {
        this.grid = grid;
    }

    /** Queues an intent for the next tick; walks to a tile nobody can stand on are refused straight away. */
    public void submit(MovementIntent intent) {
        if (intent instanceof WalkTo walk && !grid.isWalkable(walk.target())) {
            throw new InvalidMoveException();
        }
        intents.add(intent);
    }

    public Frame tick() {
        var moved = new LinkedHashMap<UUID, AvatarState>();
        var left = new ArrayList<UUID>();
        var settled = new ArrayList<Settled>();
        for (MovementIntent intent; (intent = intents.poll()) != null; ) {
            apply(intent, moved, left);
        }
        for (var avatar : avatars.values()) {
            advance(avatar, moved, settled);
        }
        return new Frame(List.copyOf(moved.values()), List.copyOf(left), List.copyOf(settled));
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

    /** Every tile someone holds, once per holder; duplicates would mean an overlap. */
    public List<Tile> heldTiles() {
        return avatars.values().stream().flatMap(a -> a.held().stream()).toList();
    }

    private void apply(MovementIntent intent, Map<UUID, AvatarState> moved, List<UUID> left) {
        var avatar = avatars.get(intent.userId());
        switch (intent) {
            case Enter enter when avatar == null -> enter(enter.userId(), moved);
            case WalkTo walk when avatar != null -> avatar.follow(route(avatar, walk.target()));
            case Step step
            when avatar != null && !avatar.walking() -> {
                var target =
                        new Tile(avatar.tile().x() + step.dx(), avatar.tile().y() + step.dy());
                if (grid.isWalkable(target)) {
                    avatar.follow(List.of(target));
                }
            }
            case Leave leave
            when avatar != null -> {
                avatar.held().forEach(holders::remove);
                avatars.remove(leave.userId());
                moved.remove(leave.userId());
                left.add(leave.userId());
            }
            default -> {
                // Entering twice, or moving someone who isn't on this floor, changes nothing.
            }
        }
    }

    // Plan around people standing in the way; if they block every route, plan through them and wait.
    private List<Tile> route(Avatar avatar, Tile target) {
        var around = grid.path(avatar.plannedFrom(), target, heldByOthers(avatar));
        return around.isEmpty() ? grid.path(avatar.plannedFrom(), target) : around;
    }

    private void enter(UUID userId, Map<UUID, AvatarState> moved) {
        grid.nearestFree(grid.arrival(), heldByOthers(null)).ifPresent(tile -> {
            var entered = new Avatar(userId, tile);
            avatars.put(userId, entered);
            holders.put(tile, entered);
            moved.put(userId, entered.state());
        });
    }

    private void advance(Avatar avatar, Map<UUID, AvatarState> moved, List<Settled> settled) {
        var from = avatar.tile();
        var landed = avatar.advanceStep();
        if (landed) {
            holders.remove(from);
        }
        var started = !avatar.stepping() && !avatar.path().isEmpty() && tryNextStep(avatar);
        if (landed || started) {
            moved.put(avatar.userId(), avatar.state());
        }
        if (avatar.walking()) {
            avatar.resetStillness();
        } else if (avatar.standStill() == SETTLE_TICKS) {
            settled.add(new Settled(avatar.userId(), avatar.tile()));
        }
    }

    /** Starts the next step if its tile is free; otherwise waits, re-routes or gives up. */
    private boolean tryNextStep(Avatar avatar) {
        var into = avatar.path().peek();
        var taken = heldByOthers(avatar);
        if (!taken.contains(into)) {
            avatar.path().poll();
            avatar.startStep(into, STEP_TICKS);
            holders.put(into, avatar);
            return true;
        }
        var waited = avatar.waited();
        if (waited >= GIVE_UP_TICKS) {
            avatar.stopWaiting();
        } else if (waited % REPATH_TICKS == 0) {
            var goal = avatar.path().peekLast();
            var around = grid.path(avatar.tile(), goal, taken);
            // Keep waiting rather than take a detour much longer than the way we were going.
            if (!around.isEmpty() && around.size() < avatar.path().size() + 14) {
                avatar.reroute(around);
            }
        }
        return false;
    }

    /** A live view of the tiles everyone but {@code me} holds. */
    private Set<Tile> heldByOthers(Avatar me) {
        return new AbstractSet<>() {
            @Override
            public boolean contains(Object tile) {
                var holder = holders.get(tile);
                return holder != null && holder != me;
            }

            @Override
            public Iterator<Tile> iterator() {
                return holders.entrySet().stream()
                        .filter(entry -> entry.getValue() != me)
                        .map(Map.Entry::getKey)
                        .iterator();
            }

            @Override
            public int size() {
                return (int)
                        holders.values().stream().filter(holder -> holder != me).count();
            }
        };
    }
}
