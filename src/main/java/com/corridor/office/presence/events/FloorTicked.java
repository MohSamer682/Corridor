package com.corridor.office.presence.events;

import java.util.List;
import java.util.UUID;

/**
 * A floor's tick moved or removed someone. Published on the tick thread, so listeners see positions in the same
 * order the floor applied them; {@code everyone} is who stands where after the tick.
 */
public record FloorTicked(UUID organizationId, UUID floorId, List<Standing> everyone) {

    public record Standing(UUID userId, int x, int y) {}
}
