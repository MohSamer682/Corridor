package com.corridor.office.identity.infrastructure;

import com.corridor.office.identity.application.TeamView;
import com.corridor.office.identity.domain.Team;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TeamRepository extends JpaRepository<Team, UUID> {

    Optional<Team> findByIdAndOrganizationId(UUID id, UUID organizationId);

    boolean existsByOrganizationIdAndNameIgnoreCase(UUID organizationId, String name);

    @Query(
            value =
                    """
                    select new com.corridor.office.identity.application.TeamView(
                        t.id, t.name, t.color, count(u.id), t.version)
                    from Team t left join User u on u.team = t
                    where t.organization.id = :organizationId
                    group by t.id, t.name, t.color, t.version
                    order by t.name
                    """,
            countQuery = "select count(t) from Team t where t.organization.id = :organizationId")
    Page<TeamView> findViews(UUID organizationId, Pageable pageable);
}
