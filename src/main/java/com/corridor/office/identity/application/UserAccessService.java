package com.corridor.office.identity.application;

import com.corridor.office.common.domain.ConflictException;
import com.corridor.office.common.security.CurrentUser;
import com.corridor.office.common.security.Role;
import com.corridor.office.identity.domain.Team;
import com.corridor.office.identity.domain.User;
import com.corridor.office.identity.domain.events.UserApproved;
import com.corridor.office.identity.domain.events.UserDeclined;
import com.corridor.office.identity.domain.events.UserReactivated;
import com.corridor.office.identity.domain.events.UserRemoved;
import com.corridor.office.identity.domain.events.UserSuspended;
import com.corridor.office.identity.infrastructure.UserRepository;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@PreAuthorize("hasAnyRole('ADMIN', 'HR')")
public class UserAccessService {

    private final UserRepository users;
    private final ManagedUsers managed;
    private final RefreshTokenService refreshTokens;
    private final ApplicationEventPublisher events;

    UserAccessService(
            UserRepository users,
            ManagedUsers managed,
            RefreshTokenService refreshTokens,
            ApplicationEventPublisher events) {
        this.users = users;
        this.managed = managed;
        this.refreshTokens = refreshTokens;
        this.events = events;
    }

    @Transactional
    public void suspend(CurrentUser actor, UUID id) {
        var user = managed.load(actor, id);
        managed.refusePending(user);
        managed.refuseSelf(actor, id, "users.self.suspend");
        managed.refuseLastActiveAdmin(user);
        user.suspend();
        refreshTokens.revokeAllFor(id);
        events.publishEvent(new UserSuspended(id, actor.organizationId(), actor.userId(), user.displayName()));
    }

    @Transactional
    public void reactivate(CurrentUser actor, UUID id) {
        var user = managed.load(actor, id);
        managed.refusePending(user);
        user.reactivate();
        events.publishEvent(new UserReactivated(id, actor.organizationId(), actor.userId(), user.displayName()));
    }

    @Transactional
    public void approve(CurrentUser actor, UUID id, Role role, UUID teamId) {
        var user = pendingRequest(actor, id);
        managed.refuseAdminChangeByHr(actor, Role.EMPLOYEE, role);
        user.approve(role, managed.team(actor, teamId));
        events.publishEvent(new UserApproved(
                id, actor.organizationId(), actor.userId(), user.displayName(), role, Team.nameOf(user.team())));
    }

    @Transactional
    public void decline(CurrentUser actor, UUID id) {
        var request = pendingRequest(actor, id);
        users.delete(request);
        events.publishEvent(
                new UserDeclined(id, actor.organizationId(), actor.userId(), request.displayName(), request.email()));
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void remove(CurrentUser actor, UUID id) {
        var user = managed.load(actor, id);
        managed.refuseSelf(actor, id, "users.self.remove");
        managed.refuseLastActiveAdmin(user);
        users.delete(user);
        events.publishEvent(new UserRemoved(id, actor.organizationId(), actor.userId(), user.displayName()));
    }

    private User pendingRequest(CurrentUser actor, UUID id) {
        var user = managed.load(actor, id);
        if (!user.isPending()) {
            throw new ConflictException("users.notPending");
        }
        return user;
    }
}
