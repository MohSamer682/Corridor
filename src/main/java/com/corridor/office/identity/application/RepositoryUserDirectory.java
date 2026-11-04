package com.corridor.office.identity.application;

import com.corridor.office.identity.UserDirectory;
import com.corridor.office.identity.domain.User;
import com.corridor.office.identity.domain.UserStatus;
import com.corridor.office.identity.infrastructure.OrganizationRepository;
import com.corridor.office.identity.infrastructure.UserRepository;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class RepositoryUserDirectory implements UserDirectory {

    private final UserRepository users;
    private final OrganizationRepository organizations;

    RepositoryUserDirectory(UserRepository users, OrganizationRepository organizations) {
        this.users = users;
        this.organizations = organizations;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<WorkWeek> workWeek(UUID organizationId) {
        return organizations
                .findById(organizationId)
                .map(o -> new WorkWeek(o.timeZone(), o.workStartsAt(), o.weekendDays()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Member> members(UUID organizationId, UUID teamId) {
        return users.findByOrganizationIdAndStatus(organizationId, UserStatus.ACTIVE).stream()
                .filter(user -> teamId == null
                        || (user.team() != null && teamId.equals(user.team().id())))
                .sorted(Comparator.comparing(User::displayName, String.CASE_INSENSITIVE_ORDER))
                .map(user -> new Member(
                        user.id(),
                        user.displayName(),
                        user.team() == null ? null : user.team().id(),
                        user.team() == null ? null : user.team().name()))
                .toList();
    }

    @Override
    public Optional<Person> find(UUID userId) {
        return users.findById(userId).map(user -> new Person(user.displayName(), user.role()));
    }

    @Override
    public Map<UUID, Person> findAll(Collection<UUID> userIds) {
        return users.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::id, user -> new Person(user.displayName(), user.role())));
    }
}
