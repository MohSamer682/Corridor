package com.corridor.office.audit.application;

import com.corridor.office.announcements.events.AnnouncementPublished;
import com.corridor.office.announcements.events.TownHallStarted;
import com.corridor.office.audit.domain.AuditEntry;
import com.corridor.office.identity.UserDirectory;
import java.util.HashMap;
import java.util.Map;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Records broadcasts and town halls, inside the transaction that sent them. */
@Component
class AnnouncementAuditListener {

    private final AuditRecorder recorder;
    private final UserDirectory directory;

    AnnouncementAuditListener(AuditRecorder recorder, UserDirectory directory) {
        this.recorder = recorder;
        this.directory = directory;
    }

    @EventListener
    void on(AnnouncementPublished event) {
        var details = new HashMap<String, String>();
        details.put("body", event.body().length() > 120 ? event.body().substring(0, 120) + "…" : event.body());
        details.put("audience", String.valueOf(event.audience()));
        if (event.teamName() != null) {
            details.put("team", event.teamName());
        }
        record(new AuditEntry(
                event.organizationId(),
                event.actorId(),
                "announcement.published",
                "announcement",
                event.announcementId(),
                details));
    }

    @EventListener
    void on(TownHallStarted event) {
        record(new AuditEntry(
                event.organizationId(),
                event.actorId(),
                "town_hall.started",
                "town_hall",
                null,
                Map.of("name", event.title())));
    }

    private void record(AuditEntry entry) {
        recorder.record(directory
                .find(entry.actorId())
                .map(actor -> entry.withActor(actor.displayName(), actor.role().name()))
                .orElse(entry));
    }
}
