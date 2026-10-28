package com.corridor.office.identity.infrastructure;

import com.corridor.office.common.security.Role;
import com.corridor.office.identity.application.UserSummary;
import com.corridor.office.identity.domain.User;
import com.corridor.office.identity.domain.UserStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, UUID> {

    @EntityGraph(attributePaths = {"organization", "team"})
    Optional<User> findByEmail(String normalizedEmail);

    @EntityGraph(attributePaths = {"organization", "team"})
    Optional<User> findWithOrganizationById(UUID id);

    @EntityGraph(attributePaths = {"organization", "team"})
    Optional<User> findByIdAndOrganizationId(UUID id, UUID organizationId);

    boolean existsByEmail(String normalizedEmail);

    long countByOrganizationIdAndRoleAndStatus(UUID organizationId, Role role, UserStatus status);

    long countByTeamId(UUID teamId);

    @Query(
            value =
                    """
                    select new com.corridor.office.identity.application.UserSummary(
                        u.id, u.email, u.displayName, u.jobTitle, u.role, u.status, t.id, t.name)
                    from User u left join u.team t
                    where u.organization.id = :organizationId
                      and (:status is null or u.status = :status)
                      and (:teamId is null or t.id = :teamId)
                      and (:pattern is null or lower(u.displayName) like :pattern or u.email like :pattern)
                    order by u.displayName, u.id
                    """,
            countQuery =
                    """
                    select count(u) from User u left join u.team t
                    where u.organization.id = :organizationId
                      and (:status is null or u.status = :status)
                      and (:teamId is null or t.id = :teamId)
                      and (:pattern is null or lower(u.displayName) like :pattern or u.email like :pattern)
                    """)
    Page<UserSummary> findSummaries(
            UUID organizationId, UserStatus status, UUID teamId, String pattern, Pageable pageable);
}
