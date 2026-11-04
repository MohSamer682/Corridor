package com.corridor.office.meetings.domain;

import java.util.Set;

/** Where a meeting is in its life, and where it may go next. */
public enum MeetingStatus {
    SCHEDULED,
    LIVE,
    ENDED,
    CANCELLED;

    public boolean canMoveTo(MeetingStatus next) {
        return switch (this) {
            case SCHEDULED -> Set.of(LIVE, CANCELLED, ENDED).contains(next);
            case LIVE -> next == ENDED || next == CANCELLED;
            case ENDED, CANCELLED -> false;
        };
    }

    /** Still holds its room. */
    public boolean isOpen() {
        return this == SCHEDULED || this == LIVE;
    }
}
