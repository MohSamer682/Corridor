package com.corridor.office.identity.application;

import com.corridor.office.common.api.PageResponse;
import com.corridor.office.common.domain.ConflictException;
import com.corridor.office.common.security.CurrentUser;
import com.corridor.office.identity.domain.User;
import com.corridor.office.identity.domain.UserStatus;
import com.corridor.office.identity.domain.events.RoleChanged;
import com.corridor.office.identity.domain.events.UserCreated;
import com.corridor.office.identity.infrastructure.OrganizationRepository;
import com.corridor.office.identity.infrastructure.UserRepository;
import java.util.Locale;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository users;
    private final ManagedUsers managed;
    private final OrganizationRepository organizations;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;

    UserService(
            UserRepository users,
            ManagedUsers managed,
            OrganizationRepository organizations,
            PasswordEncoder passwordEncoder,
            ApplicationEventPublisher events) {
        this.users = users;
        this.managed = managed;
        this.organizations = organizations;
        this.passwordEncoder = passwordEncoder;
        this.events = events;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public PageResponse<UserSummary> list(
            CurrentUser actor, UserStatus status, UUID teamId, String query, Pageable pageable) {
        var pattern =
                query == null || query.isBlank() ? null : "%" + query.strip().toLowerCase(Locale.ROOT) + "%";
        return PageResponse.of(users.findSummaries(actor.organizationId(), status, teamId, pattern, pageable));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public UserView get(CurrentUser actor, UUID id) {
        return UserView.of(managed.load(actor, id));
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public UserView create(CurrentUser actor, NewUser newUser) {
        if (users.existsByEmail(User.normalizeEmail(newUser.email()))) {
            throw new ConflictException("users.duplicateEmail");
        }
        var user = User.register(
                organizations.getReferenceById(actor.organizationId()),
                newUser.email(),
                newUser.displayName().strip(),
                newUser.role(),
                passwordEncoder.encode(newUser.password()));
        user.changeJobTitle(newUser.jobTitle());
        user.moveToTeam(managed.team(actor, newUser.teamId()));
        users.saveAndFlush(user);
        events.publishEvent(new UserCreated(user.id(), actor.organizationId(), actor.userId()));
        return UserView.of(managed.load(actor, user.id()));
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public UserView update(CurrentUser actor, UUID id, long expectedVersion, UserChanges changes) {
        var user = managed.load(actor, id);
        if (user.version() != expectedVersion) {
            throw new ConflictException("users.stale");
        }
        var previousRole = user.role();
        var roleChanges = previousRole != changes.role();
        if (roleChanges) {
            managed.refuseSelf(actor, id, "users.self.role");
            managed.refuseAdminChangeByHr(actor, previousRole, changes.role());
            managed.refuseLastActiveAdmin(user);
        }
        user.rename(changes.displayName());
        user.changeJobTitle(changes.jobTitle());
        user.changeRole(changes.role());
        user.moveToTeam(managed.team(actor, changes.teamId()));
        users.saveAndFlush(user);
        if (roleChanges) {
            events.publishEvent(
                    new RoleChanged(id, actor.organizationId(), actor.userId(), previousRole, changes.role()));
        }
        return UserView.of(user);
    }
}
