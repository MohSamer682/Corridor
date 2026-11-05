package com.corridor.office.meetings.domain;

import com.corridor.office.common.domain.InvalidInputException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Where, when and who, checked against the booking rules. */
public record MeetingSlot(UUID roomId, String title, Instant startsAt, Instant endsAt, List<UUID> invitees) {

    public static final int MIN_MINUTES = 15;
    public static final int MAX_MINUTES = 8 * 60;
    public static final int MAX_INVITEES = 50;
    public static final int MAX_TITLE = 120;

    /** What someone asked for, before the rules are checked. */
    public record Draft(UUID roomId, String title, Instant startsAt, int minutes, List<UUID> invitees) {}

    public static MeetingSlot of(Draft draft, Instant now) {
        var title = draft.title();
        var startsAt = draft.startsAt();
        var minutes = draft.minutes();
        var name = title == null ? "" : title.strip();
        if (name.isEmpty() || name.length() > MAX_TITLE) {
            throw new InvalidInputException("meetings.title");
        }
        // No start time means "now": a meeting that begins straight away, off the quarter-hour grid.
        var startsNow = startsAt == null;
        if (startsNow) {
            startsAt = now.truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        } else if (!startsAt.isAfter(now)) {
            throw new InvalidInputException("meetings.past");
        }
        if (minutes < MIN_MINUTES || minutes > MAX_MINUTES || minutes % MIN_MINUTES != 0) {
            throw new InvalidInputException("meetings.length");
        }
        if (!startsNow && startsAt.getEpochSecond() % (MIN_MINUTES * 60) != 0) {
            throw new InvalidInputException("meetings.step");
        }
        var people = draft.invitees() == null
                ? List.<UUID>of()
                : draft.invitees().stream().distinct().toList();
        if (people.size() > MAX_INVITEES) {
            throw new InvalidInputException("meetings.invitees");
        }
        return new MeetingSlot(draft.roomId(), name, startsAt, startsAt.plus(Duration.ofMinutes(minutes)), people);
    }
}
