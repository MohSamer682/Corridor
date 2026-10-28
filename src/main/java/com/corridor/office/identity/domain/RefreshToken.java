package com.corridor.office.identity.domain;

import com.corridor.office.common.domain.AuditedEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private UUID familyId;

    @Column(nullable = false)
    private byte[] tokenHash;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant revokedAt;

    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
    @JoinColumn(name = "replaced_by_id")
    private RefreshToken replacedBy;

    protected RefreshToken() {}

    private RefreshToken(User user, UUID familyId, byte[] tokenHash, Instant expiresAt) {
        this.user = user;
        this.familyId = familyId;
        this.tokenHash = tokenHash.clone();
        this.expiresAt = expiresAt;
    }

    public static RefreshToken issue(User user, UUID familyId, byte[] tokenHash, Instant expiresAt) {
        return new RefreshToken(user, familyId, tokenHash, expiresAt);
    }

    public RefreshToken rotateTo(byte[] newHash, Instant now, Instant expiresAt) {
        revoke(now);
        replacedBy = new RefreshToken(user, familyId, newHash, expiresAt);
        return replacedBy;
    }

    public boolean isUsable(Instant now) {
        return revokedAt == null && replacedBy == null && now.isBefore(expiresAt);
    }

    public void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }

    public User user() {
        return user;
    }

    public UUID familyId() {
        return familyId;
    }
}
