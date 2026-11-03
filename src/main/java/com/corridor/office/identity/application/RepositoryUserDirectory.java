package com.corridor.office.identity.application;

import com.corridor.office.identity.UserDirectory;
import com.corridor.office.identity.domain.User;
import com.corridor.office.identity.infrastructure.UserRepository;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
class RepositoryUserDirectory implements UserDirectory {

    private final UserRepository users;

    RepositoryUserDirectory(UserRepository users) {
        this.users = users;
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
