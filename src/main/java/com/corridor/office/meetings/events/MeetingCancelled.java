package com.corridor.office.meetings.events;

import java.time.Instant;
import java.util.UUID;

/** The organizer cancelled a meeting; published inside the transaction that did it. */
public record MeetingCancelled(
        UUID meetingId, UUID organizationId, UUID actorId, String title, String roomName, Instant startsAt) {}
