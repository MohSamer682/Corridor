package com.corridor.office.audit.domain;

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
}
