package com.corridor.office.insights.application;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Two of the Command Center's three queries: everyone with their first punch today, and daily head counts. */
@Component
class PeopleToday {

    /** {@code firstIn} is today's first punch, null when they haven't come in; {@code in} is punched in now. */
    record Person(UUID id, String name, UUID teamId, String team, Instant firstIn, boolean in) {}

    private final JdbcClient jdbc;

    PeopleToday(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    List<Person> people(UUID organizationId, Instant dayStart, Instant dayEnd) {
        return jdbc.sql(
                        """
                        SELECT u.id, u.display_name, u.team_id, t.name AS team_name,
                               LEAST((SELECT min(p.started_at) FROM punches p
                                      WHERE p.organization_id = :org AND p.user_id = u.id
                                        AND p.started_at >= :from AND p.started_at < :to),
                                     (SELECT o.started_at FROM open_punches o
                                      WHERE o.user_id = u.id AND o.started_at >= :from AND o.started_at < :to)) AS first_in,
                               EXISTS (SELECT 1 FROM open_punches o WHERE o.user_id = u.id) AS punched_in
                        FROM users u LEFT JOIN teams t ON t.id = u.team_id
                        WHERE u.organization_id = :org AND u.status = 'ACTIVE'
                        ORDER BY u.display_name""")
                .param("org", organizationId)
                .param("from", Timestamp.from(dayStart))
                .param("to", Timestamp.from(dayEnd))
                .query((rs, row) -> {
                    var firstIn = rs.getTimestamp("first_in");
                    return new Person(
                            rs.getObject("id", UUID.class),
                            rs.getString("display_name"),
                            rs.getObject("team_id", UUID.class),
                            rs.getString("team_name"),
                            firstIn == null ? null : firstIn.toInstant(),
                            rs.getBoolean("punched_in"));
                })
                .list();
    }

    /** How many of these people punched in on each day since {@code from}; days with nobody are left out. */
    Map<LocalDate, Integer> present(UUID organizationId, Collection<UUID> people, Instant from, ZoneId zone) {
        var days = new HashMap<LocalDate, Integer>();
        jdbc.sql(
                        """
                        SELECT (x.started_at AT TIME ZONE :zone)::date AS day, count(DISTINCT x.user_id) AS present
                        FROM (SELECT user_id, started_at FROM punches WHERE organization_id = :org AND started_at >= :from
                              UNION ALL
                              SELECT user_id, started_at FROM open_punches WHERE organization_id = :org AND started_at >= :from) x
                        WHERE x.user_id = ANY(:people)
                        GROUP BY 1""")
                .param("zone", zone.getId())
                .param("org", organizationId)
                .param("from", Timestamp.from(from))
                .param("people", people.toArray(UUID[]::new))
                .query(rs -> {
                    days.put(rs.getDate("day").toLocalDate(), rs.getInt("present"));
                });
        return days;
    }
}
