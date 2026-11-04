package com.corridor.office.presence.domain;

import java.util.UUID;

/** Where someone is, in tiles, which way they face (radians, 0 = south) and whether they are walking. */
public record AvatarState(UUID userId, double x, double y, double facing, boolean moving) {}
