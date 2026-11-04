package com.corridor.office.conversations.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/** A chat message, kept with the people it reached so only they can read it back. */
@Entity
@Table(name = "messages")
public class Message {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID organizationId;

    @Column(nullable = false, updatable = false)
    private UUID floorId;

    @Column(nullable = false, updatable = false)
    private UUID senderId;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, updatable = false)
    private UUID[] recipients;

    @Column(nullable = false, updatable = false)
    private String body;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Message() {}

    public Message(UUID organizationId, UUID floorId, UUID senderId, List<UUID> recipients, String body) {
        this.organizationId = organizationId;
        this.floorId = floorId;
        this.senderId = senderId;
        this.recipients = recipients.toArray(UUID[]::new);
        this.body = body;
        this.createdAt = Instant.now();
    }

    public UUID id() {
        return id;
    }

    public UUID senderId() {
        return senderId;
    }

    public List<UUID> recipients() {
        return List.of(recipients);
    }

    public String body() {
        return body;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
