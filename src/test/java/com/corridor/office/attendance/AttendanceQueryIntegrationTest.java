package com.corridor.office.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.corridor.office.TestcontainersConfiguration;
import com.corridor.office.attendance.application.AttendanceQuery;
import com.corridor.office.attendance.application.AttendanceQuery.TeamDay;
import com.corridor.office.attendance.domain.WorkDay;
import com.corridor.office.common.domain.ForbiddenException;
import com.corridor.office.common.security.CurrentUser;
import com.corridor.office.identity.infrastructure.UserRepository;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;
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
class AttendanceQueryIntegrationTest {

    private static final ZoneId CAIRO = ZoneId.of("Africa/Cairo");

    @Autowired
    AttendanceQuery attendance;

    @Autowired
    UserRepository users;

    @Autowired
    JdbcClient jdbc;

    private CurrentUser nour;
    private CurrentUser omar;
    private CurrentUser salma;
    private CurrentUser hr;
    private UUID product;
    private final LocalDate yesterday = LocalDate.now(CAIRO).minusDays(1);

    private CurrentUser person(String email) {
        var u = users.findByEmail(email).orElseThrow();
        return new CurrentUser(u.id(), u.organization().id(), u.role());
    }

    @BeforeEach
    void putNourAndSalmaOnOneTeam() {
        nour = person("nour@corridor.local");
        omar = person("omar@corridor.local");
        salma = person("manager@corridor.local");
        var admin = person("admin@corridor.local");
        hr = new CurrentUser(admin.userId(), admin.organizationId(), com.corridor.office.common.security.Role.HR);
        jdbc.sql("DELETE FROM punches").update();
        jdbc.sql(
                        "UPDATE users SET team_id = NULL WHERE team_id IN (SELECT id FROM teams WHERE name = 'Attendance test')")
                .update();
        jdbc.sql("DELETE FROM teams WHERE name = 'Attendance test'").update();
        product = UUID.randomUUID();
        jdbc.sql(
                        """
                        INSERT INTO teams (id, organization_id, name, color, created_at, updated_at)
                        VALUES (:id, :org, 'Attendance test', '#2F7D6D', now(), now())""")
                .param("id", product)
                .param("org", nour.organizationId())
                .update();
        jdbc.sql("UPDATE users SET team_id = :team WHERE id IN (:a, :b)")
                .param("team", product)
                .param("a", nour.userId())
                .param("b", salma.userId())
                .update();
        punch(nour, yesterday, "09:25", "13:00");
        punch(nour, yesterday, "13:30", "17:42");
    }

    private void punch(CurrentUser who, LocalDate day, String from, String to) {
        jdbc.sql(
                        """
                        INSERT INTO punches (id, organization_id, user_id, started_at, ended_at)
                        VALUES (gen_random_uuid(), :org, :user, :from, :to)""")
                .param("org", who.organizationId())
                .param("user", who.userId())
                .param(
                        "from",
                        Timestamp.from(
                                day.atTime(LocalTime.parse(from)).atZone(CAIRO).toInstant()))
                .param(
                        "to",
                        Timestamp.from(
                                day.atTime(LocalTime.parse(to)).atZone(CAIRO).toInstant()))
                .update();
    }

    @Test
    void shouldShowMyDaysWithLateMinutesAndIntervals() {
        var days = attendance.mine(nour, yesterday, yesterday);

        assertThat(days).singleElement().satisfies(day -> {
            assertThat(day.lateMinutes()).isEqualTo(25);
            assertThat(day.intervals()).hasSize(2);
            assertThat(day.workedMinutes()).isEqualTo(215 + 252);
        });
    }

    @Test
    void shouldShowManagersOnlyTheirOwnTeam() {
        var page = attendance.team(salma, yesterday, null, "", 0);

        assertThat(page.items()).extracting(TeamDay::displayName).containsExactly("Nour Hassan", "Salma Ibrahim");
        assertThat(page.items().getFirst().day().status()).isEqualTo(WorkDay.Status.PRESENT);
        assertThatThrownBy(() -> attendance.person(salma, omar.userId(), yesterday, yesterday))
                .isInstanceOf(ForbiddenException.class);
        assertThat(attendance.person(salma, nour.userId(), yesterday, yesterday))
                .hasSize(1);
    }

    @Test
    void shouldShowHrEveryoneAndKeepEmployeesOut() {
        var page = attendance.team(hr, yesterday, null, "omar", 0);

        assertThat(page.items()).extracting(TeamDay::displayName).containsExactly("Omar Adel");
        assertThatThrownBy(() -> attendance.team(nour, yesterday, null, "", 0)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> attendance.person(nour, omar.userId(), yesterday, yesterday))
                .isInstanceOf(ForbiddenException.class);
    }
}
