package com.corridor.office.insights.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.corridor.office.TestcontainersConfiguration;
import com.corridor.office.common.domain.ForbiddenException;
import com.corridor.office.common.security.CurrentUser;
import com.corridor.office.conversations.FocusStatus;
import com.corridor.office.identity.infrastructure.UserRepository;
import com.corridor.office.presence.OnlinePeople;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
        properties = {
            "spring.flyway.locations=classpath:db/migration,classpath:db/seed",
            "spring.flyway.out-of-order=true"
        })
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class CommandCenterIntegrationTest {

    // Thursday 5 November 2026, 10:00 in Cairo; work starts at 09:00 and the weekend is Friday and Saturday.
    private static final Instant TEN_AM = Instant.parse("2026-11-05T08:00:00Z");
    private static final UUID NILE = UUID.fromString("2dabfd95-c0fa-4311-adca-1f73cf1af37c");
    private static final UUID FLOOR = UUID.randomUUID();

    // Who is on a floor and in focus only lives in memory; the test says it directly.
    @MockitoBean
    LiveSignals live;

    @Autowired
    CommandCenterService commandCenter;

    @Autowired
    UserRepository users;

    @Autowired
    JdbcClient jdbc;

    private CurrentUser admin;
    private CurrentUser salma;
    private CurrentUser nour;
    private CurrentUser omar;
    private UUID team;

    private CurrentUser person(String email) {
        var u = users.findByEmail(email).orElseThrow();
        return new CurrentUser(u.id(), u.organization().id(), u.role());
    }

    @BeforeEach
    void setTheMorning() {
        admin = person("admin@corridor.local");
        salma = person("manager@corridor.local");
        nour = person("nour@corridor.local");
        omar = person("omar@corridor.local");
        cleanUp();
        team = UUID.randomUUID();
        jdbc.sql(
                        """
                        INSERT INTO teams (id, organization_id, name, color, created_at, updated_at)
                        VALUES (:id, :org, 'CC test', '#2F7D6D', now(), now())""")
                .param("id", team)
                .param("org", admin.organizationId())
                .update();
        jdbc.sql("UPDATE users SET team_id = :team WHERE id IN (:people)")
                .param("team", team)
                .param("people", List.of(salma.userId(), nour.userId()))
                .update();
        // Nour came in at 09:20 and is still in; Salma worked 08:55 to 09:30; Omar worked on Tuesday only.
        openPunch(nour, "2026-11-05T07:20:00Z");
        punch(salma, "2026-11-05T06:55:00Z", "2026-11-05T07:30:00Z");
        punch(omar, "2026-11-03T06:50:00Z", "2026-11-03T15:00:00Z");
        meeting("CC standup", "2026-11-05T07:30:00Z", "2026-11-05T08:30:00Z", salma, nour, omar);
        OnlinePeople online = org -> Map.of(nour.userId(), FLOOR, omar.userId(), FLOOR);
        FocusStatus focus = id -> id.equals(nour.userId());
        when(live.online()).thenReturn(online);
        when(live.focus()).thenReturn(focus);
    }

    @AfterEach
    void cleanUp() {
        jdbc.sql("DELETE FROM open_punches").update();
        jdbc.sql("DELETE FROM punches").update();
        jdbc.sql("DELETE FROM meetings WHERE title LIKE 'CC %'").update();
        jdbc.sql("DELETE FROM users WHERE email LIKE 'cc-perf-%'").update();
        jdbc.sql("UPDATE users SET team_id = NULL WHERE team_id IN (SELECT id FROM teams WHERE name = 'CC test')")
                .update();
        jdbc.sql("DELETE FROM teams WHERE name = 'CC test'").update();
    }

    @Test
    void shouldCountTheWholeCompanyForAnAdmin() {
        var view = commandCenter.snapshot(admin, TEN_AM);

        var kpis = view.kpis();
        assertThat(kpis.online()).isEqualTo(2);
        assertThat(kpis.inFocus()).isEqualTo(1);
        assertThat(kpis.inMeetings()).isEqualTo(3);
        assertThat(kpis.punchedIn()).isEqualTo(1);
        assertThat(kpis.late()).isEqualTo(1);
        assertThat(kpis.roomsInUse()).isEqualTo(1);
        assertThat(view.floors()).containsExactly(new CommandCenter.FloorCount(FLOOR, 2));
        assertThat(view.rooms())
                .filteredOn(room -> room.roomId().equals(NILE))
                .singleElement()
                .satisfies(room -> {
                    assertThat(room.meeting()).isEqualTo("CC standup");
                    assertThat(room.people()).isEqualTo(3);
                });
        assertThat(view.attention()).contains("Omar has not clocked in.").doesNotContain("Nour has not clocked in.");
        assertThat(view.days()).hasSize(14);
        assertThat(view.days().getLast()).isEqualTo(new CommandCenter.DayCount(LocalDate.of(2026, 11, 5), 2, false));
        assertThat(view.days())
                .filteredOn(day -> day.day().equals(LocalDate.of(2026, 11, 3)))
                .singleElement()
                .isEqualTo(new CommandCenter.DayCount(LocalDate.of(2026, 11, 3), 1, false));
        assertThat(view.days())
                .filteredOn(day -> day.day().equals(LocalDate.of(2026, 10, 30)))
                .singleElement()
                .extracting(CommandCenter.DayCount::weekend)
                .isEqualTo(true);
    }

    @Test
    void shouldShowAManagerOnlyTheirTeam() {
        var view = commandCenter.snapshot(salma, TEN_AM);

        assertThat(view.scope()).isEqualTo("CC test");
        assertThat(view.kpis().people()).isEqualTo(2);
        assertThat(view.kpis().online()).isEqualTo(1);
        assertThat(view.kpis().late()).isEqualTo(1);
        assertThat(view.teams()).singleElement().isEqualTo(new CommandCenter.TeamRow("CC test", 2, 1, 1, 1, 2));
        assertThat(view.attention()).noneMatch(line -> line.startsWith("Omar"));
    }

    @Test
    void shouldKeepEmployeesOut() {
        assertThatThrownBy(() -> commandCenter.snapshot(nour, TEN_AM)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void shouldAnswerQuicklyForAThousandPeople() {
        jdbc.sql(
                        """
                        INSERT INTO users (id, organization_id, email, display_name, role, status, created_at, updated_at,
                                           team_id)
                        SELECT gen_random_uuid(), :org, 'cc-perf-' || n || '@corridor.local', 'Person ' || n, 'EMPLOYEE',
                               'ACTIVE', now(), now(), :team
                        FROM generate_series(1, 1000) n""")
                .param("org", admin.organizationId())
                .param("team", team)
                .update();
        jdbc.sql(
                        """
                        INSERT INTO open_punches (id, organization_id, user_id, started_at)
                        SELECT gen_random_uuid(), organization_id, id, :at FROM users WHERE email LIKE 'cc-perf-%'""")
                .param("at", Timestamp.from(Instant.parse("2026-11-05T06:58:00Z")))
                .update();
        commandCenter.snapshot(admin, TEN_AM);

        var started = System.nanoTime();
        var view = commandCenter.snapshot(admin, TEN_AM);
        var millis = (System.nanoTime() - started) / 1_000_000;

        assertThat(view.kpis().punchedIn()).isEqualTo(1001);
        assertThat(millis).isLessThan(200);
    }

    private void openPunch(CurrentUser who, String at) {
        jdbc.sql(
                        """
                        INSERT INTO open_punches (id, organization_id, user_id, started_at)
                        VALUES (gen_random_uuid(), :org, :user, :at)""")
                .param("org", who.organizationId())
                .param("user", who.userId())
                .param("at", Timestamp.from(Instant.parse(at)))
                .update();
    }

    private void punch(CurrentUser who, String from, String to) {
        jdbc.sql(
                        """
                        INSERT INTO punches (id, organization_id, user_id, started_at, ended_at)
                        VALUES (gen_random_uuid(), :org, :user, :from, :to)""")
                .param("org", who.organizationId())
                .param("user", who.userId())
                .param("from", Timestamp.from(Instant.parse(from)))
                .param("to", Timestamp.from(Instant.parse(to)))
                .update();
    }

    private void meeting(String title, String from, String to, CurrentUser organizer, CurrentUser... invited) {
        var id = UUID.randomUUID();
        jdbc.sql(
                        """
                        INSERT INTO meetings (id, organization_id, room_id, organizer_id, title, starts_at, ends_at, status,
                                              created_at, updated_at)
                        VALUES (:id, :org, :room, :organizer, :title, :from, :to, 'LIVE', now(), now())""")
                .param("id", id)
                .param("org", organizer.organizationId())
                .param("room", NILE)
                .param("organizer", organizer.userId())
                .param("title", title)
                .param("from", Timestamp.from(Instant.parse(from)))
                .param("to", Timestamp.from(Instant.parse(to)))
                .update();
        for (var person : invited) {
            jdbc.sql("INSERT INTO meeting_attendees (meeting_id, user_id) VALUES (:id, :user)")
                    .param("id", id)
                    .param("user", person.userId())
                    .update();
        }
    }
}
