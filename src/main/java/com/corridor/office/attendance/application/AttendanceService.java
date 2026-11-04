package com.corridor.office.attendance.application;

import com.corridor.office.attendance.events.PunchedIn;
import com.corridor.office.attendance.events.PunchedOut;
import com.corridor.office.attendance.infrastructure.PunchStore;
import com.corridor.office.common.domain.ConflictException;
import com.corridor.office.common.idempotency.IdempotencyGuard;
import com.corridor.office.common.security.CurrentUser;
import com.corridor.office.identity.UserDirectory;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Punching in and out, once per press: the database allows one open punch, the guard one answer per key. */
@Service
public class AttendanceService {

    /** Where you stand now, and what today adds up to so far. */
    public record PunchView(boolean punchedIn, Instant since, Instant lastOut, long workedTodayMinutes) {}

    private final PunchStore punches;
    private final IdempotencyGuard guard;
    private final UserDirectory directory;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    AttendanceService(
            PunchStore punches,
            IdempotencyGuard guard,
            UserDirectory directory,
            ApplicationEventPublisher events,
            Clock clock) {
        this.punches = punches;
        this.guard = guard;
        this.directory = directory;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public PunchView punchIn(CurrentUser person, UUID key) {
        return guard.once(person.userId(), key, PunchView.class, () -> {
            var now = clock.instant();
            if (!punches.open(newId(), person.organizationId(), person.userId(), now)) {
                throw new ConflictException("attendance.alreadyIn");
            }
            events.publishEvent(new PunchedIn(person.organizationId(), person.userId(), now));
            return new PunchView(true, now, null, workedToday(person, now));
        });
    }

    @Transactional
    public PunchView punchOut(CurrentUser person, UUID key) {
        return guard.once(person.userId(), key, PunchView.class, () -> {
            var interval = punches.close(person.userId(), clock.instant())
                    .orElseThrow(() -> new ConflictException("attendance.notIn"));
            events.publishEvent(
                    new PunchedOut(person.organizationId(), person.userId(), interval.from(), interval.to()));
            return new PunchView(false, null, interval.to(), workedToday(person, interval.to()));
        });
    }

    @Transactional(readOnly = true)
    public PunchView status(CurrentUser person) {
        var now = clock.instant();
        var since = punches.openSince(person.userId());
        return new PunchView(since.isPresent(), since.orElse(null), null, workedToday(person, now));
    }

    // Today in the company's time zone; an open punch counts up to now.
    private long workedToday(CurrentUser person, Instant now) {
        var zone = directory
                .workWeek(person.organizationId())
                .map(UserDirectory.WorkWeek::zone)
                .orElse(ZoneId.of("Africa/Cairo"));
        var start = now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant();
        var closed = punches.minutesBetween(person.userId(), start, start.plus(Duration.ofDays(1)));
        var open = punches.openSince(person.userId())
                .map(since -> Duration.between(since.isBefore(start) ? start : since, now)
                        .toMinutes())
                .orElse(0L);
        return closed + open;
    }

    private static UUID newId() {
        return UuidV7.next();
    }
}
