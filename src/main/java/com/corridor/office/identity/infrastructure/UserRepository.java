package com.corridor.office.identity.infrastructure;

import com.corridor.office.identity.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    @EntityGraph(attributePaths = "organization")
    Optional<User> findByEmail(String normalizedEmail);

    @EntityGraph(attributePaths = "organization")
    Optional<User> findWithOrganizationById(UUID id);
}
