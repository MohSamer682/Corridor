package com.corridor.office.identity.application;

import com.corridor.office.common.security.CurrentUser;
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
@PreAuthorize("hasRole('ADMIN')")
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
        managed.refuseSelf(actor, id, "users.self.suspend");
        managed.refuseLastActiveAdmin(user);
        user.suspend();
        refreshTokens.revokeAllFor(id);
        events.publishEvent(new UserSuspended(id, actor.organizationId(), actor.userId()));
    }

    @Transactional
    public void reactivate(CurrentUser actor, UUID id) {
        managed.load(actor, id).reactivate();
        events.publishEvent(new UserReactivated(id, actor.organizationId(), actor.userId()));
    }

    @Transactional
    public void remove(CurrentUser actor, UUID id) {
        var user = managed.load(actor, id);
        managed.refuseSelf(actor, id, "users.self.remove");
        managed.refuseLastActiveAdmin(user);
        users.delete(user);
        events.publishEvent(new UserRemoved(id, actor.organizationId(), actor.userId()));
    }
}
