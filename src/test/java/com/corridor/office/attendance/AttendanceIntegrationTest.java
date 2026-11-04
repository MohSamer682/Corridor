package com.corridor.office.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.corridor.office.TestcontainersConfiguration;
import com.corridor.office.attendance.application.AttendanceService;
import com.corridor.office.attendance.application.AttendanceService.PunchView;
import com.corridor.office.common.domain.ConflictException;
import com.corridor.office.common.security.CurrentUser;
import com.corridor.office.identity.infrastructure.UserRepository;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
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
class AttendanceIntegrationTest {

    @Autowired
    AttendanceService attendance;

    @Autowired
    UserRepository users;

    @Autowired
    JdbcClient jdbc;

    private CurrentUser nour;

    @BeforeEach
    void startTheDayClean() {
        var n = users.findByEmail("nour@corridor.local").orElseThrow();
        nour = new CurrentUser(n.id(), n.organization().id(), n.role());
        jdbc.sql("DELETE FROM open_punches").update();
        jdbc.sql("DELETE FROM punches").update();
        jdbc.sql("DELETE FROM idempotency_keys").update();
    }

    private long count(String table) {
        return jdbc.sql("SELECT count(*) FROM " + table + " WHERE user_id = :me")
                .param("me", nour.userId())
                .query(Long.class)
                .single();
    }

    @Test
    void shouldPunchInOnceAndRefuseASecondPunchIn() {
        var first = attendance.punchIn(nour, UUID.randomUUID());

        assertThat(first.punchedIn()).isTrue();
        assertThatThrownBy(() -> attendance.punchIn(nour, UUID.randomUUID()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("attendance.alreadyIn");
        assertThat(count("open_punches")).isOne();
    }

    @Test
    void shouldReplayTheFirstResponseForTheSameKey() {
        var key = UUID.randomUUID();

        var first = attendance.punchIn(nour, key);
        var again = attendance.punchIn(nour, key);

        assertThat(again).isEqualTo(first);
        assertThat(count("open_punches")).isOne();
    }

    @Test
    void shouldMoveTheOpenPunchIntoPunchesOnPunchOut() {
        attendance.punchIn(nour, UUID.randomUUID());

        var out = attendance.punchOut(nour, UUID.randomUUID());

        assertThat(out.punchedIn()).isFalse();
        assertThat(out.lastOut()).isNotNull();
        assertThat(count("open_punches")).isZero();
        assertThat(count("punches")).isOne();
        assertThatThrownBy(() -> attendance.punchOut(nour, UUID.randomUUID()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("attendance.notIn");
    }

    @Test
    void shouldAuditEveryPunch() {
        attendance.punchIn(nour, UUID.randomUUID());
        attendance.punchOut(nour, UUID.randomUUID());

        var actions = jdbc.sql("SELECT action FROM audit_events WHERE actor_id = :me ORDER BY sequence DESC LIMIT 2")
                .param("me", nour.userId())
                .query(String.class)
                .list();
        assertThat(actions).containsExactly("attendance.punched_out", "attendance.punched_in");
    }

    @Test
    void shouldKeepOneOpenPunchUnderFiftyParallelRequests() throws Exception {
        var pool = Executors.newFixedThreadPool(50);
        var start = new CountDownLatch(1);
        var tasks = new ArrayList<Callable<PunchView>>();
        for (int i = 0; i < 50; i++) {
            tasks.add(() -> {
                start.await();
                try {
                    return attendance.punchIn(nour, UUID.randomUUID());
                } catch (ConflictException alreadyIn) {
                    return null;
                }
            });
        }
        var futures = tasks.stream().map(pool::submit).toList();
        start.countDown();
        var succeeded = 0;
        for (var future : futures) {
            if (future.get() != null) {
                succeeded++;
            }
        }
        pool.shutdown();

        assertThat(succeeded).isOne();
        assertThat(count("open_punches")).isOne();
    }

    @Test
    void shouldLeaveOnePunchWhenTwoPunchOutsRace() throws Exception {
        attendance.punchIn(nour, UUID.randomUUID());
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        Callable<Boolean> out = () -> {
            start.await();
            try {
                attendance.punchOut(nour, UUID.randomUUID());
                return true;
            } catch (ConflictException notIn) {
                return false;
            }
        };
        var a = pool.submit(out);
        var b = pool.submit(out);
        start.countDown();

        assertThat(a.get() ^ b.get()).isTrue();
        pool.shutdown();
        assertThat(count("punches")).isOne();
    }
}
