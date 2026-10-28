package com.corridor.office.identity.infrastructure;

import com.corridor.office.identity.domain.Team;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamRepository extends JpaRepository<Team, UUID> {

    Optional<Team> findByIdAndOrganizationId(UUID id, UUID organizationId);

    boolean existsByOrganizationIdAndNameIgnoreCase(UUID organizationId, String name);

    Page<Team> findAllByOrganizationIdOrderByName(UUID organizationId, Pageable pageable);
}
