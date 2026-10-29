package com.corridor.office.presence.domain;

import java.util.UUID;

/** Where someone stands, or the tile they are stepping into while moving. Facing is N, S, E or W. */
public record AvatarState(UUID userId, int x, int y, char facing, boolean moving) {}
