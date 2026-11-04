package com.corridor.office.audit.application;

import com.corridor.office.audit.domain.AuditEntry;
import com.corridor.office.identity.UserDirectory;
import com.corridor.office.meetings.events.MeetingCancelled;
import java.util.Map;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Records cancelled meetings, inside the transaction that cancelled them. */
@Component
class MeetingAuditListener {

    private final AuditRecorder recorder;
    private final UserDirectory directory;

    MeetingAuditListener(AuditRecorder recorder, UserDirectory directory) {
        this.recorder = recorder;
        this.directory = directory;
    }

    @EventListener
    void on(MeetingCancelled event) {
        var entry = new AuditEntry(
                event.organizationId(),
                event.actorId(),
                "meeting.cancelled",
                "meeting",
                event.meetingId(),
                Map.of(
                        "name",
                        event.title(),
                        "room",
                        event.roomName(),
                        "at",
                        event.startsAt().toString()));
        recorder.record(directory
                .find(event.actorId())
                .map(actor -> entry.withActor(actor.displayName(), actor.role().name()))
                .orElse(entry));
    }
}
