package com.corridor.office.spaces.infrastructure;

import com.corridor.office.spaces.domain.FloorLayout;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FloorLayoutRepository extends JpaRepository<FloorLayout, UUID> {

    Optional<FloorLayout> findByFloorIdAndVersion(UUID floorId, int version);
}
