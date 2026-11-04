package com.corridor.office.attendance.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * One person's day, worked out from their punches whenever it is read. A punch belongs to the date it started on in
 * the company's time zone, so a night shift counts toward the evening it began.
 */
public record WorkDay(
        LocalDate day, Status status, List<Span> intervals, long workedMinutes, long lateMinutes, boolean inNow) {

    public enum Status {
        PRESENT,
        ABSENT,
        WEEKEND,
        NOT_IN_YET
    }

    public static WorkDay of(LocalDate day, List<Span> spans, WorkWeekRules week, Instant now) {
        var intervals = spans.stream().sorted(Comparator.comparing(Span::from)).toList();
        var worked = intervals.stream()
                .mapToLong(span -> Duration.between(span.from(), span.to() == null ? now : span.to())
                        .toMinutes())
                .sum();
        var weekend = week.weekend().contains(day.getDayOfWeek());
        var late = 0L;
        if (!intervals.isEmpty() && !weekend) {
            var start = day.atTime(week.startsAt()).atZone(week.zone()).toInstant();
            late = Math.max(
                    0, Duration.between(start, intervals.getFirst().from()).toMinutes());
        }
        var inNow = intervals.stream().anyMatch(span -> span.to() == null);
        return new WorkDay(day, status(day, intervals, weekend, week, now), intervals, worked, late, inNow);
    }

    private static Status status(
            LocalDate day, List<Span> intervals, boolean weekend, WorkWeekRules week, Instant now) {
        if (!intervals.isEmpty()) {
            return Status.PRESENT;
        }
        if (weekend) {
            return Status.WEEKEND;
        }
        return day.isBefore(now.atZone(week.zone()).toLocalDate()) ? Status.ABSENT : Status.NOT_IN_YET;
    }
}
