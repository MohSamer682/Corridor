package com.corridor.office.presence.domain;

import com.corridor.office.spaces.TileGrid.Tile;
import java.util.UUID;

/** Someone has stood still on a tile long enough to count as sitting there. */
public record Settled(UUID userId, Tile tile) {}
