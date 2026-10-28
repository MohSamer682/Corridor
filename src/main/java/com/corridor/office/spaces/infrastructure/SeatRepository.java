package com.corridor.office.spaces.infrastructure;

import com.corridor.office.spaces.domain.Seat;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeatRepository extends JpaRepository<Seat, UUID> {

    List<Seat> findByFloorId(UUID floorId);
}
