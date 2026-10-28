package com.corridor.office.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "audit_events")
public class AuditEvent {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID organizationId;

    @Column(nullable = false, updatable = false)
    private long sequence;

    @Column(updatable = false)
    private UUID actorId;

    @Column(nullable = false, updatable = false)
    private String action;

    @Column(nullable = false, updatable = false)
    private String targetType;

    @Column(updatable = false)
    private UUID targetId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    private Map<String, String> details;

    @Column(updatable = false)
    private byte[] previousHash;

    @Column(nullable = false, updatable = false)
    private byte[] hash;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditEvent() {}

    /** Appends to the chain after {@code previous}, or starts it when {@code previous} is null. */
    public static AuditEvent append(AuditEvent previous, AuditEntry entry, Instant at) {
        var event = new AuditEvent();
        event.organizationId = entry.organizationId();
        event.sequence = previous == null ? 1 : previous.sequence + 1;
        event.actorId = entry.actorId();
        event.action = entry.action();
        event.targetType = entry.targetType();
        event.targetId = entry.targetId();
        event.details = entry.details();
        // Postgres keeps microseconds; hashing finer time would never verify after a read.
        event.createdAt = at.truncatedTo(ChronoUnit.MICROS);
        event.previousHash = previous == null ? null : previous.hash;
        event.hash = AuditChain.hash(event);
        return event;
    }

    public UUID id() {
        return id;
    }

    public UUID organizationId() {
        return organizationId;
    }

    public long sequence() {
        return sequence;
    }

    public UUID actorId() {
        return actorId;
    }

    public String action() {
        return action;
    }

    public String targetType() {
        return targetType;
    }

    public UUID targetId() {
        return targetId;
    }

    public Map<String, String> details() {
        return details;
    }

    public byte[] previousHash() {
        return previousHash == null ? null : previousHash.clone();
    }

    public byte[] hash() {
        return hash.clone();
    }

    public Instant createdAt() {
        return createdAt;
    }
}
