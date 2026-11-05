package com.corridor.office.spaces;

import java.util.List;
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

    /** The rooms of a floor that shut out sound: offices and meeting rooms. Empty for a floor of another company. */
    List<PrivateRoom> privateRooms(UUID organizationId, UUID floorId);

    /** The company's town hall room and the point in its middle, if it has one. */
    Optional<Place> townHall(UUID organizationId);

    /** A point on a floor, in tiles. */
    record Place(UUID floorId, double x, double y, String name) {}

    /** Meeting and conference rooms of a company, by building, floor and name. */
    List<BookableRoom> bookableRooms(UUID organizationId);

    /** A room meetings can be booked in, and where it is ("Corridor HQ · L2"). */
    record BookableRoom(UUID id, String name, UUID floorId, String place) {}

    /** A room whose walls run along its bounds; you are inside when you stand within the walls. */
    record PrivateRoom(UUID id, String name, int minX, int minY, int maxX, int maxY) {

        public boolean contains(int x, int y) {
            return x > minX && x < maxX && y > minY && y < maxY;
        }
    }
}
