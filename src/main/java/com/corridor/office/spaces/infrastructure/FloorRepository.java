package com.corridor.office.spaces.infrastructure;

import com.corridor.office.spaces.domain.Floor;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FloorRepository extends JpaRepository<Floor, UUID> {

    @Query("select f from Floor f where f.id = :id and f.building.organizationId = :organizationId")
    Optional<Floor> findInOrganization(UUID id, UUID organizationId);
}
