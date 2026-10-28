package com.corridor.office.spaces.infrastructure;

import com.corridor.office.spaces.domain.Building;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BuildingRepository extends JpaRepository<Building, UUID> {

    @Query(
            "select distinct b from Building b left join fetch b.floors where b.organizationId = :organizationId order by b.sortOrder")
    List<Building> findCampus(UUID organizationId);
}
