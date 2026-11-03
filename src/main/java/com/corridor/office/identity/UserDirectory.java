package com.corridor.office.identity;

import com.corridor.office.common.security.Role;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Read-only lookup of people for other modules. */
public interface UserDirectory {

    Optional<Person> find(UUID userId);

    /** Names and roles of several people at once; unknown ids are left out. */
    Map<UUID, Person> findAll(Collection<UUID> userIds);

    record Person(String displayName, Role role) {}
}
