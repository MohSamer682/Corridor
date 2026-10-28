package com.corridor.office.identity.application;

import com.corridor.office.identity.UserDirectory;
import com.corridor.office.identity.infrastructure.UserRepository;
import java.util.Optional;
import java.util.UUID;
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
}
