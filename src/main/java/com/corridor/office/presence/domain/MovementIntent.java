package com.corridor.office.presence.domain;

import com.corridor.office.spaces.TileGrid.Tile;
import java.util.UUID;

/** What a person asked for; queued and applied on the floor tick (Command). */
public sealed interface MovementIntent {

    UUID userId();

    record Enter(UUID userId) implements MovementIntent {}

    record WalkTo(UUID userId, Tile target) implements MovementIntent {}

    /** One tile in a direction, for keyboard walking; dx and dy are -1, 0 or 1. */
    record Step(UUID userId, int dx, int dy) implements MovementIntent {}

    record Leave(UUID userId) implements MovementIntent {}
}
