package com.corridor.office.attendance.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WorkDayTest {

    private static final ZoneId CAIRO = ZoneId.of("Africa/Cairo");
    private static final WorkWeekRules WEEK =
            new WorkWeekRules(CAIRO, LocalTime.of(9, 0), Set.of(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY));
    // Wednesday 4 November 2026.
    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 11, 4);
    private static final Instant EVENING = at(WEDNESDAY, "20:00");

    private static Instant at(LocalDate day, String time) {
        return day.atTime(LocalTime.parse(time)).atZone(CAIRO).toInstant();
    }

    private static Span span(LocalDate day, String from, String to) {
        return new Span(at(day, from), to == null ? null : at(day, to));
    }

    @Test
    void shouldCountMinutesLateFromTheFirstPunchIn() {
        var day = WorkDay.of(WEDNESDAY, List.of(span(WEDNESDAY, "09:25", "17:42")), WEEK, EVENING);

        assertThat(day.lateMinutes()).isEqualTo(25);
        assertThat(day.workedMinutes()).isEqualTo(497);
        assertThat(day.status()).isEqualTo(WorkDay.Status.PRESENT);
    }

    @Test
    void shouldAddUpSeveralIntervalsAndNeverBeLateWhenEarly() {
        var day = WorkDay.of(
                WEDNESDAY,
                List.of(span(WEDNESDAY, "13:40", "17:42"), span(WEDNESDAY, "08:52", "13:05")),
                WEEK,
                EVENING);

        assertThat(day.lateMinutes()).isZero();
        assertThat(day.workedMinutes()).isEqualTo(253 + 242);
        assertThat(day.intervals()).extracting(Span::from).isSorted();
    }

    @Test
    void shouldCountAnOpenPunchUpToNow() {
        var now = at(WEDNESDAY, "12:43");

        var day = WorkDay.of(WEDNESDAY, List.of(span(WEDNESDAY, "09:25", null)), WEEK, now);

        assertThat(day.workedMinutes()).isEqualTo(198);
        assertThat(day.inNow()).isTrue();
    }

    @Test
    void shouldKeepAShiftAcrossMidnightOnTheDayItStarted() {
        var night = new Span(at(WEDNESDAY, "23:50"), at(WEDNESDAY.plusDays(1), "00:40"));

        var day = WorkDay.of(WEDNESDAY, List.of(night), WEEK, EVENING.plusSeconds(86_400));

        assertThat(day.workedMinutes()).isEqualTo(50);
    }

    @Test
    void shouldMarkWeekendsAndMissedWorkingDays() {
        var friday = LocalDate.of(2026, 11, 6);
        var tuesday = WEDNESDAY.minusDays(1);

        assertThat(WorkDay.of(friday, List.of(), WEEK, at(friday.plusDays(1), "10:00"))
                        .status())
                .isEqualTo(WorkDay.Status.WEEKEND);
        assertThat(WorkDay.of(tuesday, List.of(), WEEK, EVENING).status()).isEqualTo(WorkDay.Status.ABSENT);
        assertThat(WorkDay.of(WEDNESDAY, List.of(), WEEK, EVENING).status()).isEqualTo(WorkDay.Status.NOT_IN_YET);
    }

    @Test
    void shouldNotCallWorkOnAWeekendLate() {
        var saturday = LocalDate.of(2026, 11, 7);

        var day = WorkDay.of(saturday, List.of(span(saturday, "11:00", "13:00")), WEEK, at(saturday, "14:00"));

        assertThat(day.lateMinutes()).isZero();
        assertThat(day.status()).isEqualTo(WorkDay.Status.PRESENT);
    }
}
