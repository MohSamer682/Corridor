package com.corridor.office.audit.application;

import com.corridor.office.attendance.events.PunchedIn;
import com.corridor.office.attendance.events.PunchedOut;
import com.corridor.office.audit.domain.AuditEntry;
import com.corridor.office.identity.UserDirectory;
import java.time.Duration;
import java.util.Map;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Every punch goes into the audit log with its times, so HR can read anyone's day (Muhammed, 2026-10-28). */
@Component
class AttendanceAuditListener {

    private final AuditRecorder recorder;
    private final UserDirectory directory;

    AttendanceAuditListener(AuditRecorder recorder, UserDirectory directory) {
        this.recorder = recorder;
        this.directory = directory;
    }

    @EventListener
    void on(PunchedIn event) {
        record(new AuditEntry(
                event.organizationId(),
                event.userId(),
                "attendance.punched_in",
                "user",
                event.userId(),
                Map.of("at", event.at().toString())));
    }

    @EventListener
    void on(PunchedOut event) {
        var minutes = Duration.between(event.from(), event.to()).toMinutes();
        record(new AuditEntry(
                event.organizationId(),
                event.userId(),
                "attendance.punched_out",
                "user",
                event.userId(),
                Map.of(
                        "from",
                        event.from().toString(),
                        "to",
                        event.to().toString(),
                        "minutes",
                        String.valueOf(minutes))));
    }

    private void record(AuditEntry entry) {
        recorder.record(directory
                .find(entry.actorId())
                .map(actor -> entry.withActor(actor.displayName(), actor.role().name()))
                .orElse(entry));
    }
}
