package com.corridor.office.spaces.infrastructure;

import com.corridor.office.spaces.domain.Room;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomRepository extends JpaRepository<Room, UUID> {

    List<Room> findByFloorIdOrderByName(UUID floorId);
}
