package com.corridor.office.audit.application;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.UUID;

/** Position after the last event of a slice; opaque to clients. */
record AuditCursor(Instant createdAt, UUID id) {

    String encode() {
        var raw = createdAt + " " + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    static AuditCursor decode(String cursor) {
        try {
            var raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            var parts = raw.split(" ", 2);
            return new AuditCursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
        } catch (IllegalArgumentException | DateTimeParseException | ArrayIndexOutOfBoundsException broken) {
            throw new InvalidAuditQueryException("audit.badCursor");
        }
    }
}
