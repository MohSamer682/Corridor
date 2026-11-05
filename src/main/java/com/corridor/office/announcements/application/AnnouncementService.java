package com.corridor.office.announcements.application;

import com.corridor.office.announcements.events.AnnouncementPublished;
import com.corridor.office.common.domain.InvalidInputException;
import com.corridor.office.common.domain.NotFoundException;
import com.corridor.office.common.security.CurrentUser;
import com.corridor.office.identity.UserDirectory;
import com.corridor.office.identity.UserDirectory.Member;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Announcements to everyone or one team. Each one is stored, pushed to whoever is connected, and stays unread for
 * each person until they dismiss it, so people who were away or in focus see it later.
 */
@Service
public class AnnouncementService {

    public static final int MAX_LENGTH = 1_000;

    public record AnnouncementView(
            UUID id, String kind, String body, String teamName, UUID authorId, String authorName, Instant createdAt) {}

    /** How many people it is for, and how many of them were connected when it went out. */
    public record Sent(UUID id, int audience, int online) {}

    private final JdbcClient jdbc;
    private final UserDirectory directory;
    private final SimpMessagingTemplate sockets;
    private final SimpUserRegistry connected;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    AnnouncementService(
            JdbcClient jdbc,
            UserDirectory directory,
            SimpMessagingTemplate sockets,
            SimpUserRegistry connected,
            AnnouncementEvents events) {
        this.jdbc = jdbc;
        this.directory = directory;
        this.sockets = sockets;
        this.connected = connected;
        this.events = events.publisher();
        this.clock = events.clock();
    }

    @Transactional
    public Sent publish(CurrentUser author, UUID teamId, String body) {
        Broadcasters.check(author);
        var text = body == null ? "" : body.strip();
        if (text.isEmpty() || text.length() > MAX_LENGTH) {
            throw new InvalidInputException("announcements.bodyLength");
        }
        var audience = directory.members(author.organizationId(), teamId);
        if (teamId != null && audience.isEmpty()) {
            throw new NotFoundException("announcements.teamNotFound");
        }
        var id = UUID.randomUUID();
        var now = clock.instant();
        store(id, author, teamId, text, now);
        var teamName = teamId == null ? null : audience.getFirst().teamName();
        var view =
                new AnnouncementView(id, "ANNOUNCEMENT", text, teamName, author.userId(), name(author.userId()), now);
        var online = deliver(audience, view);
        events.publishEvent(new AnnouncementPublished(
                id, author.organizationId(), author.userId(), teamName, text, audience.size()));
        return new Sent(id, audience.size(), online);
    }

    /** What the person has not dismissed yet, from the last 7 days, newest first. */
    @Transactional(readOnly = true)
    public List<AnnouncementView> unread(CurrentUser reader) {
        return jdbc.sql(
                        """
                        SELECT a.id, a.body, a.author_id, a.created_at, t.name AS team_name
                        FROM announcements a LEFT JOIN teams t ON t.id = a.team_id
                        WHERE a.organization_id = :org AND a.created_at > :since
                          AND (a.team_id IS NULL OR a.team_id = :team)
                          AND NOT EXISTS (SELECT 1 FROM announcement_reads r
                                          WHERE r.announcement_id = a.id AND r.user_id = :me)
                        ORDER BY a.created_at DESC, a.id DESC""")
                .param("org", reader.organizationId())
                .param("since", Timestamp.from(clock.instant().minusSeconds(7 * 24 * 3600)))
                .param("team", teamOf(reader))
                .param("me", reader.userId())
                .query((rs, row) -> row(rs))
                .list();
    }

    @Transactional
    public void markRead(CurrentUser reader, UUID announcementId) {
        jdbc.sql(
                        """
                        INSERT INTO announcement_reads (announcement_id, user_id, read_at)
                        SELECT id, :me, :now FROM announcements WHERE id = :id AND organization_id = :org
                        ON CONFLICT DO NOTHING""")
                .param("me", reader.userId())
                .param("now", Timestamp.from(clock.instant()))
                .param("id", announcementId)
                .param("org", reader.organizationId())
                .update();
    }

    private void store(UUID id, CurrentUser author, UUID teamId, String text, Instant now) {
        jdbc.sql(
                        """
                        INSERT INTO announcements (id, organization_id, author_id, team_id, body, created_at)
                        VALUES (:id, :org, :author, :team, :body, :now)""")
                .param("id", id)
                .param("org", author.organizationId())
                .param("author", author.userId())
                .param("team", teamId)
                .param("body", text)
                .param("now", Timestamp.from(now))
                .update();
    }

    // Pushed to whoever is connected now; everyone else finds it unread when they next open Corridor.
    private int deliver(List<Member> audience, AnnouncementView view) {
        var online = 0;
        for (Member member : audience) {
            if (connected.getUser(member.userId().toString()) != null) {
                online++;
                sockets.convertAndSendToUser(member.userId().toString(), "/queue/announcements", view);
            }
        }
        return online;
    }

    private UUID teamOf(CurrentUser reader) {
        return directory.members(reader.organizationId(), null).stream()
                .filter(m -> m.userId().equals(reader.userId()))
                .findFirst()
                .map(Member::teamId)
                .orElse(null);
    }

    private AnnouncementView row(ResultSet rs) throws SQLException {
        var authorId = rs.getObject("author_id", UUID.class);
        return new AnnouncementView(
                rs.getObject("id", UUID.class),
                "ANNOUNCEMENT",
                rs.getString("body"),
                rs.getString("team_name"),
                authorId,
                name(authorId),
                rs.getTimestamp("created_at").toInstant());
    }

    private String name(UUID userId) {
        return directory.find(userId).map(UserDirectory.Person::displayName).orElse("");
    }
}
