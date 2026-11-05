package com.corridor.office.meetings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.corridor.office.TestcontainersConfiguration;
import com.corridor.office.common.domain.ForbiddenException;
import com.corridor.office.common.domain.InvalidInputException;
import com.corridor.office.common.domain.NotFoundException;
import com.corridor.office.common.security.CurrentUser;
import com.corridor.office.identity.infrastructure.UserRepository;
import com.corridor.office.meetings.application.MeetingService;
import com.corridor.office.meetings.application.MeetingService.MeetingView;
import com.corridor.office.meetings.application.MeetingService.ScheduleMeeting;
import com.corridor.office.meetings.domain.RoomAlreadyBookedException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
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
class MeetingsIntegrationTest {

    // From the local seed: a meeting room and a private office on L1 Engineering.
    private static final UUID NILE = UUID.fromString("2dabfd95-c0fa-4311-adca-1f73cf1af37c");
    private static final UUID BACKEND_LEAD_OFFICE = UUID.fromString("85c72a57-11f7-47b1-a9e7-05bac9446dd4");

    @Autowired
    MeetingService meetings;

    @Autowired
    UserRepository users;

    @Autowired
    JdbcClient jdbc;

    private CurrentUser nour;
    private CurrentUser omar;
    private Instant ten;

    private CurrentUser person(String email) {
        var u = users.findByEmail(email).orElseThrow();
        return new CurrentUser(u.id(), u.organization().id(), u.role());
    }

    @BeforeEach
    void clearTheCalendar() {
        jdbc.sql("DELETE FROM meeting_attendees").update();
        jdbc.sql("DELETE FROM meetings").update();
        nour = person("nour@corridor.local");
        omar = person("omar@corridor.local");
        ten = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
    }

    private MeetingView book(CurrentUser who, String title, Instant start, int minutes) {
        return meetings.schedule(
                who, new ScheduleMeeting(NILE, title, start, minutes, List.of(omar.userId())), UUID.randomUUID());
    }

    @Test
    void shouldBookAFreeSlotAndListItForTheInvitee() {
        var booked = book(nour, "Release planning", ten, 30);

        assertThat(booked.roomName()).isEqualTo("Nile Room");
        assertThat(meetings.list(omar, ten.minus(1, ChronoUnit.HOURS), ten.plus(1, ChronoUnit.HOURS)))
                .extracting(MeetingView::title)
                .containsExactly("Release planning");
    }

    @Test
    void shouldRefuseAnOverlappingBookingWithTheClash() {
        book(nour, "Release planning", ten, 30);

        assertThatThrownBy(() -> book(omar, "Roadmap check-in", ten.plus(15, ChronoUnit.MINUTES), 30))
                .isInstanceOf(RoomAlreadyBookedException.class)
                .satisfies(e -> assertThat(((RoomAlreadyBookedException) e).arguments())
                        .containsExactly("Nile Room", "Release planning"));
        book(omar, "Back to back", ten.plus(30, ChronoUnit.MINUTES), 30);
    }

    @Test
    void shouldBookOnceWhenTwoRequestsRaceForTheSameSlot() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        Callable<Boolean> attempt = () -> {
            start.await();
            try {
                book(nour, "Race", ten, 60);
                return true;
            } catch (RoomAlreadyBookedException clash) {
                return false;
            }
        };
        var a = pool.submit(attempt);
        var b = pool.submit(attempt);
        start.countDown();

        assertThat(a.get() ^ b.get()).isTrue();
        pool.shutdown();
    }

    @Test
    void shouldFreeTheRoomWhenAMeetingIsCancelled() {
        var booked = book(nour, "Release planning", ten, 30);

        assertThatThrownBy(() -> meetings.cancel(omar, booked.id())).isInstanceOf(ForbiddenException.class);
        meetings.cancel(nour, booked.id());

        book(omar, "Took the slot", ten, 30);
        assertThat(jdbc.sql("SELECT count(*) FROM audit_events WHERE action = 'meeting.cancelled'")
                        .query(Long.class)
                        .single())
                .isPositive();
    }

    @Test
    void shouldCheckTheRulesBeforeBooking() {
        var past = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.HOURS);

        assertThatThrownBy(() -> book(nour, "Too late", past, 30)).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> book(nour, "Too short", ten, 10)).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> book(nour, "Too long", ten, 9 * 60)).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> book(nour, "x".repeat(121), ten, 30)).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> book(nour, "Off the grid", ten.plus(7, ChronoUnit.MINUTES), 30))
                .isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> meetings.schedule(
                        nour,
                        new ScheduleMeeting(BACKEND_LEAD_OFFICE, "In an office", ten, 30, List.of()),
                        UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void shouldMeetNowWithoutAStartTime() {
        var now = meetings.schedule(
                nour, new ScheduleMeeting(NILE, "Quick sync", null, 30, List.of(omar.userId())), UUID.randomUUID());

        assertThat(now.status()).isEqualTo(com.corridor.office.meetings.domain.MeetingStatus.LIVE);
        assertThat(now.startsAt()).isBeforeOrEqualTo(Instant.now());
        assertThat(now.endsAt()).isAfter(Instant.now().plus(29, ChronoUnit.MINUTES));
    }

    @Test
    void shouldLetTheOrganizerStartEarlyWhenTheRoomIsFree() {
        var later = book(nour, "Design review", ten, 45);

        assertThatThrownBy(() -> meetings.startNow(omar, later.id())).isInstanceOf(ForbiddenException.class);
        var started = meetings.startNow(nour, later.id());

        assertThat(started.status()).isEqualTo(com.corridor.office.meetings.domain.MeetingStatus.LIVE);
        assertThat(started.startsAt()).isBefore(ten);
        assertThat(java.time.Duration.between(started.startsAt(), started.endsAt()))
                .hasMinutes(45);
    }

    @Test
    void shouldNotStartEarlyOverSomeoneElsesBooking() {
        // The next quarter hour is taken; my meeting is later, so starting it now would run into that one.
        var nextQuarter = Instant.ofEpochSecond((Instant.now().getEpochSecond() / 900 + 1) * 900);
        book(omar, "Already here", nextQuarter, 15);
        var mine = book(nour, "Mine later", nextQuarter.plus(1, ChronoUnit.HOURS), 30);

        assertThatThrownBy(() -> meetings.startNow(nour, mine.id())).isInstanceOf(RoomAlreadyBookedException.class);
    }

    @Test
    void shouldBookOnceForTheSameKey() {
        var key = UUID.randomUUID();
        var request = new ScheduleMeeting(NILE, "Double click", ten, 30, List.of());

        var first = meetings.schedule(nour, request, key);
        var again = meetings.schedule(nour, request, key);

        assertThat(again.id()).isEqualTo(first.id());
    }
}
