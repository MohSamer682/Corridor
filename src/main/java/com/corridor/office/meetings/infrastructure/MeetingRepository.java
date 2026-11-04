package com.corridor.office.meetings.infrastructure;

import com.corridor.office.meetings.domain.Meeting;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MeetingRepository extends JpaRepository<Meeting, UUID> {

    @Query(
            """
            select m from Meeting m
            where m.roomId = :roomId and m.startsAt < :endsAt and m.endsAt > :startsAt and m.id <> :except
              and m.status in (com.corridor.office.meetings.domain.MeetingStatus.SCHEDULED,
                               com.corridor.office.meetings.domain.MeetingStatus.LIVE)""")
    List<Meeting> clashes(UUID roomId, Instant startsAt, Instant endsAt, UUID except);

    @Query(
            """
            select distinct m from Meeting m left join fetch m.attendees
            where m.organizationId = :organizationId and m.startsAt < :to and m.endsAt > :from
              and (m.organizerId = :viewer or :viewer member of m.attendees)
            order by m.startsAt""")
    List<Meeting> forPerson(UUID organizationId, UUID viewer, Instant from, Instant to);

    @Query(
            """
            select distinct m from Meeting m left join fetch m.attendees
            where m.organizationId = :organizationId and m.roomId = :roomId and m.startsAt < :to and m.endsAt > :from
            order by m.startsAt""")
    List<Meeting> inRoom(UUID organizationId, UUID roomId, Instant from, Instant to);

    @Query(
            "select m from Meeting m left join fetch m.attendees where m.id = :id and m.organizationId = :organizationId")
    Optional<Meeting> findInOrganization(UUID id, UUID organizationId);
}
