package com.corridor.office.audit.domain;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public record AuditEntry(
        UUID organizationId,
        UUID actorId,
        String action,
        String targetType,
        UUID targetId,
        Map<String, String> details) {

    public AuditEntry {
        details = Map.copyOf(details);
    }

    /** Copies the actor's name and role in, so the entry still reads right after the actor is removed. */
    public AuditEntry withActor(String actorName, String actorRole) {
        var withActor = new HashMap<>(details);
        withActor.put("actorName", actorName);
        withActor.put("actorRole", actorRole);
        return new AuditEntry(organizationId, actorId, action, targetType, targetId, withActor);
    }
}
