package com.corridor.office.spaces;

import java.util.Optional;
import java.util.UUID;

/** What other modules may ask about floors. */
public interface SpaceCatalog {

    /** The walkable grid of a floor, if the floor belongs to that organization. */
    Optional<TileGrid> grid(UUID organizationId, UUID floorId);
}
