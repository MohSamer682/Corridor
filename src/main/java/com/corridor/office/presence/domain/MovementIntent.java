package com.corridor.office.presence.domain;

import java.util.UUID;

/** What a person asked for; queued and applied on the floor tick (Command). */
public sealed interface MovementIntent {

    UUID userId();

    record Enter(UUID userId) implements MovementIntent {}

    /**
     * Where the person's browser says they are now, in tiles (x and y grow east and south). The browser walks the
     * avatar itself, like the prototype; the floor only accepts positions that are possible.
     */
    record Move(UUID userId, double x, double y, double facing, boolean moving) implements MovementIntent {}

    record Leave(UUID userId) implements MovementIntent {}
}
