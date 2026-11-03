package com.corridor.office.spaces;

import java.util.Optional;
import java.util.UUID;

/** What other modules may ask about floors. */
public interface SpaceCatalog {

    /** The walkable grid of a floor, if the floor belongs to that organization. */
    Optional<TileGrid> grid(UUID organizationId, UUID floorId);

    /**
     * Someone has sat at a tile long enough to count. A free desk there becomes theirs, and the desk they
     * had before is freed; anything else (a sofa, someone else's desk, an office desk) changes nothing.
     */
    void sat(UUID organizationId, UUID floorId, UUID userId, TileGrid.Tile tile);
}
