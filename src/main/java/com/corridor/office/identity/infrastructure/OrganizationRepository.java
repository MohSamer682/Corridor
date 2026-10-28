package com.corridor.office.identity.infrastructure;

import com.corridor.office.identity.domain.Organization;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

    Optional<Organization> findFirstByOrderByCreatedAtAsc();
}
