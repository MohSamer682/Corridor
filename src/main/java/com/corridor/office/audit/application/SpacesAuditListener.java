package com.corridor.office.audit.application;

import com.corridor.office.audit.domain.AuditEntry;
import com.corridor.office.identity.UserDirectory;
import com.corridor.office.spaces.events.OfficeAssigned;
import java.util.HashMap;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Records office assignments, inside the transaction that made them. */
@Component
class SpacesAuditListener {

    private final AuditRecorder recorder;
    private final UserDirectory directory;

    SpacesAuditListener(AuditRecorder recorder, UserDirectory directory) {
        this.recorder = recorder;
        this.directory = directory;
    }

    @EventListener
    void on(OfficeAssigned event) {
        var details = new HashMap<String, String>();
        details.put("room", event.roomName());
        if (event.ownerId() != null) {
            directory.find(event.ownerId()).ifPresent(owner -> details.put("name", owner.displayName()));
        }
        var entry = new AuditEntry(
                event.organizationId(), event.actorId(), "room.owner_assigned", "room", event.roomId(), details);
        recorder.record(directory
                .find(event.actorId())
                .map(actor -> entry.withActor(actor.displayName(), actor.role().name()))
                .orElse(entry));
    }
}
