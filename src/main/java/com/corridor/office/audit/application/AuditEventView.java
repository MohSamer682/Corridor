package com.corridor.office.audit.application;

import com.corridor.office.audit.domain.AuditEvent;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditEventView(
        UUID id,
        String action,
        Instant createdAt,
        UUID actorId,
        String targetType,
        UUID targetId,
        Map<String, String> details) {

    static AuditEventView of(AuditEvent event) {
        return new AuditEventView(
                event.id(),
                event.action(),
                event.createdAt(),
                event.actorId(),
                event.targetType(),
                event.targetId(),
                event.details());
    }
}
