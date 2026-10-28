package com.corridor.office.identity.application;

import com.corridor.office.common.api.PageResponse;
import com.corridor.office.common.domain.ConflictException;
import com.corridor.office.common.domain.NotFoundException;
import com.corridor.office.common.security.CurrentUser;
import com.corridor.office.identity.domain.Team;
import com.corridor.office.identity.infrastructure.OrganizationRepository;
import com.corridor.office.identity.infrastructure.TeamRepository;
import com.corridor.office.identity.infrastructure.UserRepository;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TeamService {

    private final TeamRepository teams;
    private final UserRepository users;
    private final OrganizationRepository organizations;

    public TeamService(TeamRepository teams, UserRepository users, OrganizationRepository organizations) {
        this.teams = teams;
        this.users = users;
        this.organizations = organizations;
    }

    @Transactional(readOnly = true)
    public PageResponse<TeamView> list(CurrentUser actor, Pageable pageable) {
        return PageResponse.of(teams.findViews(actor.organizationId(), pageable));
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public TeamView create(CurrentUser actor, String name, String color) {
        refuseDuplicateName(actor, name);
        var team = Team.create(organizations.getReferenceById(actor.organizationId()), name, color);
        teams.saveAndFlush(team);
        return new TeamView(team.id(), team.name(), team.color(), 0, team.version());
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public TeamView update(CurrentUser actor, UUID id, long expectedVersion, String name, String color) {
        var team = load(actor, id);
        if (team.version() != expectedVersion) {
            throw new ConflictException("teams.stale");
        }
        if (!team.name().equalsIgnoreCase(name.strip())) {
            refuseDuplicateName(actor, name);
        }
        team.rename(name);
        team.recolor(color);
        teams.saveAndFlush(team);
        return new TeamView(team.id(), team.name(), team.color(), users.countByTeamId(id), team.version());
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public void delete(CurrentUser actor, UUID id) {
        var team = load(actor, id);
        if (users.countByTeamId(id) > 0) {
            throw new ConflictException("teams.notEmpty");
        }
        teams.delete(team);
    }

    private Team load(CurrentUser actor, UUID id) {
        return teams.findByIdAndOrganizationId(id, actor.organizationId())
                .orElseThrow(() -> new NotFoundException("teams.notFound"));
    }

    private void refuseDuplicateName(CurrentUser actor, String name) {
        if (teams.existsByOrganizationIdAndNameIgnoreCase(actor.organizationId(), name.strip())) {
            throw new ConflictException("teams.duplicate");
        }
    }
}
