package com.corridor.office.spaces.infrastructure;

import com.corridor.office.spaces.domain.Room;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RoomRepository extends JpaRepository<Room, UUID> {

    List<Room> findByFloorIdOrderByName(UUID floorId);

    Optional<Room> findByOwnerId(UUID ownerId);

    @Query(
            """
            select r from Room r, Floor f
            where f.id = r.floorId and f.building.organizationId = :organizationId
              and r.kind = com.corridor.office.spaces.domain.RoomKind.OFFICE
            order by f.building.name, f.level, r.name""")
    List<Room> findOffices(UUID organizationId);

    @Query(
            """
            select r from Room r, Floor f
            where f.id = r.floorId and f.building.organizationId = :organizationId
              and r.kind in (com.corridor.office.spaces.domain.RoomKind.MEETING,
                             com.corridor.office.spaces.domain.RoomKind.CONFERENCE)
            order by f.building.name, f.level, r.name""")
    List<Room> findBookable(UUID organizationId);

    @Query(
            """
            select r from Room r, Floor f
            where f.id = r.floorId and f.building.organizationId = :organizationId
              and r.kind = com.corridor.office.spaces.domain.RoomKind.HALL
            order by f.level, r.name""")
    List<Room> findHalls(UUID organizationId);
}
