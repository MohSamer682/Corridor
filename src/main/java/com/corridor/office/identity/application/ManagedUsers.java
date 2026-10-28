package com.corridor.office.identity.application;

import com.corridor.office.common.domain.ConflictException;
import com.corridor.office.common.domain.NotFoundException;
import com.corridor.office.common.security.CurrentUser;
import com.corridor.office.common.security.Role;
import com.corridor.office.identity.domain.Team;
import com.corridor.office.identity.domain.User;
import com.corridor.office.identity.domain.UserStatus;
import com.corridor.office.identity.infrastructure.TeamRepository;
import com.corridor.office.identity.infrastructure.UserRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Lookups scoped to the caller's organization and the rules every admin action on a person shares. */
@Component
class ManagedUsers {

    private final UserRepository users;
    private final TeamRepository teams;

    ManagedUsers(UserRepository users, TeamRepository teams) {
        this.users = users;
        this.teams = teams;
    }

    User load(CurrentUser actor, UUID id) {
        return users.findByIdAndOrganizationId(id, actor.organizationId())
                .orElseThrow(() -> new NotFoundException("users.notFound"));
    }

    Team team(CurrentUser actor, UUID teamId) {
        if (teamId == null) {
            return null;
        }
        return teams.findByIdAndOrganizationId(teamId, actor.organizationId())
                .orElseThrow(() -> new NotFoundException("teams.notFound"));
    }

    void refuseSelf(CurrentUser actor, UUID id, String messageKey) {
        if (actor.userId().equals(id)) {
            throw new ConflictException(messageKey);
        }
    }

    // Only an active admin counts; demoting, suspending or removing the last one would lock everyone out.
    void refuseLastActiveAdmin(User user) {
        if (user.role() == Role.ADMIN
                && user.isActive()
                && users.countByOrganizationIdAndRoleAndStatus(
                                user.organization().id(), Role.ADMIN, UserStatus.ACTIVE)
                        <= 1) {
            throw new ConflictException("users.lastAdmin");
        }
    }
}
