package com.corridor.office.identity;

import com.corridor.office.common.security.Role;
import java.util.Optional;
import java.util.UUID;

/** Read-only lookup of people for other modules. */
public interface UserDirectory {

    Optional<Person> find(UUID userId);

    record Person(String displayName, Role role) {}
}
