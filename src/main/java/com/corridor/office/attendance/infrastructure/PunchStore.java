package com.corridor.office.attendance.infrastructure;

import com.corridor.office.attendance.domain.Span;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Open and finished punches. Plain SQL, because the punch-out must move a row in one statement. */
@Repository
public class PunchStore {

    public record Interval(Instant from, Instant to) {}

    public record PersonSpan(UUID userId, Span span) {}

    private final JdbcClient jdbc;

    PunchStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Opens a punch; false when one is already open (the unique constraint decides, not a read). */
    public boolean open(UUID id, UUID organizationId, UUID userId, Instant at) {
        return jdbc.sql(
                                """
                        INSERT INTO open_punches (id, organization_id, user_id, started_at)
                        VALUES (:id, :org, :user, :at)
                        ON CONFLICT (user_id) DO NOTHING""")
                        .param("id", id)
                        .param("org", organizationId)
                        .param("user", userId)
                        .param("at", Timestamp.from(at))
                        .update()
                == 1;
    }

    /** Moves the open punch into punches in one statement; empty when there was none. */
    public Optional<Interval> close(UUID userId, Instant at) {
        return jdbc.sql(
                        """
                        WITH moved AS (DELETE FROM open_punches WHERE user_id = :user RETURNING *)
                        INSERT INTO punches (id, organization_id, user_id, started_at, ended_at)
                        SELECT id, organization_id, user_id, started_at, greatest(:at, started_at) FROM moved
                        RETURNING started_at, ended_at""")
                .param("user", userId)
                .param("at", Timestamp.from(at))
                .query((rs, row) -> new Interval(
                        rs.getTimestamp("started_at").toInstant(),
                        rs.getTimestamp("ended_at").toInstant()))
                .optional();
    }

    /** Finished and open punches of these people that started in [from, to). */
    public List<PersonSpan> spans(UUID organizationId, Collection<UUID> people, Instant from, Instant to) {
        return jdbc.sql(
                        """
                        SELECT user_id, started_at, ended_at FROM punches
                        WHERE organization_id = :org AND user_id IN (:people) AND started_at >= :from AND started_at < :to
                        UNION ALL
                        SELECT user_id, started_at, NULL FROM open_punches
                        WHERE organization_id = :org AND user_id IN (:people) AND started_at >= :from AND started_at < :to""")
                .param("org", organizationId)
                .param("people", people)
                .param("from", Timestamp.from(from))
                .param("to", Timestamp.from(to))
                .query((rs, row) -> {
                    var ended = rs.getTimestamp("ended_at");
                    return new PersonSpan(
                            rs.getObject("user_id", UUID.class),
                            new Span(
                                    rs.getTimestamp("started_at").toInstant(),
                                    ended == null ? null : ended.toInstant()));
                })
                .list();
    }

    public Optional<Instant> openSince(UUID userId) {
        return jdbc.sql("SELECT started_at FROM open_punches WHERE user_id = :user")
                .param("user", userId)
                .query(Timestamp.class)
                .optional()
                .map(Timestamp::toInstant);
    }

    /** Minutes of finished punches that started in [from, to). */
    public long minutesBetween(UUID userId, Instant from, Instant to) {
        return jdbc.sql(
                        """
                        SELECT coalesce(sum(extract(epoch FROM ended_at - started_at)) / 60, 0)::bigint FROM punches
                        WHERE user_id = :user AND started_at >= :from AND started_at < :to""")
                .param("user", userId)
                .param("from", Timestamp.from(from))
                .param("to", Timestamp.from(to))
                .query(Long.class)
                .single();
    }
}
