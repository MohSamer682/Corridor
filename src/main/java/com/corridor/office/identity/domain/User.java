package com.corridor.office.identity.domain;

import com.corridor.office.common.domain.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.Locale;

@Entity
@Table(name = "users")
public class User extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(nullable = false, length = 80)
    private String displayName;

    @Column(length = 80)
    private String jobTitle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private UserStatus status;

    private String passwordHash;

    @Version
    private long version;

    protected User() {}

    private User(Organization organization, String email, String displayName, Role role, String passwordHash) {
        this.organization = organization;
        this.email = normalizeEmail(email);
        this.displayName = displayName;
        this.role = role;
        this.status = UserStatus.ACTIVE;
        this.passwordHash = passwordHash;
    }

    public static User register(
            Organization organization, String email, String displayName, Role role, String passwordHash) {
        return new User(organization, email, displayName, role, passwordHash);
    }

    public static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public void suspend() {
        status = UserStatus.SUSPENDED;
    }

    public void reactivate() {
        status = UserStatus.ACTIVE;
    }

    public Organization organization() {
        return organization;
    }

    public String email() {
        return email;
    }

    public String displayName() {
        return displayName;
    }

    public String jobTitle() {
        return jobTitle;
    }

    public Role role() {
        return role;
    }

    public UserStatus status() {
        return status;
    }
}
