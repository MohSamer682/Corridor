package com.corridor.office.identity;

import com.corridor.office.common.security.Role;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Read-only lookup of people for other modules. */
public interface UserDirectory {

    Optional<Person> find(UUID userId);

    /** Names and roles of several people at once; unknown ids are left out. */
    Map<UUID, Person> findAll(Collection<UUID> userIds);

    record Person(String displayName, Role role) {}

    /** When a company's working day starts, in which time zone, and which days are the weekend. */
    Optional<WorkWeek> workWeek(UUID organizationId);

    /** Active people of a company, optionally only one team, by name. */
    List<Member> members(UUID organizationId, UUID teamId);

    record WorkWeek(ZoneId zone, LocalTime startsAt, Set<DayOfWeek> weekend) {}

    record Member(UUID userId, String displayName, UUID teamId, String teamName) {}
}
