package com.corridor.office.announcements;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.corridor.office.TestcontainersConfiguration;
import com.corridor.office.announcements.application.AnnouncementService;
import com.corridor.office.announcements.application.AnnouncementService.AnnouncementView;
import com.corridor.office.announcements.application.TownHallService;
import com.corridor.office.common.domain.ConflictException;
import com.corridor.office.common.domain.ForbiddenException;
import com.corridor.office.common.domain.InvalidInputException;
import com.corridor.office.common.security.CurrentUser;
import com.corridor.office.identity.infrastructure.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
        properties = {
            "spring.flyway.locations=classpath:db/migration,classpath:db/seed",
            "spring.flyway.out-of-order=true"
        })
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class AnnouncementsIntegrationTest {

    @Autowired
    AnnouncementService announcements;

    @Autowired
    TownHallService townHall;

    @Autowired
    UserRepository users;

    @Autowired
    JdbcClient jdbc;

    private CurrentUser nour;
    private CurrentUser omar;
    private CurrentUser salma;
    private UUID backend;

    private CurrentUser person(String email) {
        var u = users.findByEmail(email).orElseThrow();
        return new CurrentUser(u.id(), u.organization().id(), u.role());
    }

    @BeforeEach
    void putNourOnBackend() {
        nour = person("nour@corridor.local");
        omar = person("omar@corridor.local");
        salma = person("manager@corridor.local");
        jdbc.sql("DELETE FROM announcement_reads").update();
        jdbc.sql("DELETE FROM announcements").update();
        jdbc.sql("UPDATE users SET team_id = NULL WHERE team_id IN (SELECT id FROM teams WHERE name = 'Announce test')")
                .update();
        jdbc.sql("DELETE FROM teams WHERE name = 'Announce test'").update();
        backend = UUID.randomUUID();
        jdbc.sql(
                        """
                        INSERT INTO teams (id, organization_id, name, color, created_at, updated_at)
                        VALUES (:id, :org, 'Announce test', '#2F7D6D', now(), now())""")
                .param("id", backend)
                .param("org", nour.organizationId())
                .update();
        jdbc.sql("UPDATE users SET team_id = :team WHERE id = :nour")
                .param("team", backend)
                .param("nour", nour.userId())
                .update();
    }

    @AfterEach
    void endTheTownHall() {
        townHall.end(salma);
    }

    @Test
    void shouldReachOnlyTheTeamItIsFor() {
        var sent = announcements.publish(salma, backend, "Release freeze starts at 5 pm.");

        assertThat(sent.audience()).isEqualTo(1);
        assertThat(announcements.unread(nour))
                .extracting(AnnouncementView::body)
                .containsExactly("Release freeze starts at 5 pm.");
        assertThat(announcements.unread(omar)).isEmpty();
    }

    @Test
    void shouldStayUnreadUntilDismissed() {
        announcements.publish(salma, null, "Lunch is on us today.");
        var unread = announcements.unread(omar);
        assertThat(unread).hasSize(1);

        announcements.markRead(omar, unread.getFirst().id());

        assertThat(announcements.unread(omar)).isEmpty();
        assertThat(announcements.unread(nour)).hasSize(1);
    }

    @Test
    void shouldLetOnlyManagersHrAndAdminsBroadcastAndCheckTheBody() {
        assertThatThrownBy(() -> announcements.publish(nour, null, "Hi all")).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> announcements.publish(salma, null, " ")).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> announcements.publish(salma, null, "x".repeat(1_001)))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    void shouldAuditABroadcast() {
        announcements.publish(salma, null, "All hands at 3.");

        assertThat(jdbc.sql("SELECT count(*) FROM audit_events WHERE action = 'announcement.published'")
                        .query(Long.class)
                        .single())
                .isPositive();
    }

    @Test
    void shouldHoldOneTownHallAtATime() {
        var live = townHall.start(salma, "Q4 all-hands");

        assertThat(live.title()).isEqualTo("Q4 all-hands");
        assertThat(live.floorId()).isNotNull();
        assertThat(townHall.current(omar))
                .hasValueSatisfying(t -> assertThat(t.title()).isEqualTo("Q4 all-hands"));
        assertThatThrownBy(() -> townHall.start(salma, "Another one")).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> townHall.start(nour, "Mine")).isInstanceOf(ForbiddenException.class);

        townHall.end(salma);
        assertThat(townHall.current(omar)).isEmpty();
    }
}
