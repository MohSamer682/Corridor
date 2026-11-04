package com.corridor.office.conversations.domain;

import java.util.UUID;

/** Where someone stands; {@code privateRoomId} is set while they are inside a private room. */
public record Spot(UUID userId, int x, int y, UUID privateRoomId) {}
