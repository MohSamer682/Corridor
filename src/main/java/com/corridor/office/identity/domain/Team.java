package com.corridor.office.identity.domain;

import com.corridor.office.common.domain.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.Locale;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "teams")
public class Team extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(nullable = false, length = 60)
    private String name;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 7)
    private String color;

    @Version
    private long version;

    protected Team() {}

    private Team(Organization organization, String name, String color) {
        this.organization = organization;
        rename(name);
        recolor(color);
    }

    public static Team create(Organization organization, String name, String color) {
        return new Team(organization, name, color);
    }

    /** The team's name, or null for no team. */
    public static String nameOf(Team team) {
        return team == null ? null : team.name();
    }

    public void rename(String newName) {
        name = newName.strip();
    }

    public void recolor(String newColor) {
        color = newColor.toUpperCase(Locale.ROOT);
    }

    public Organization organization() {
        return organization;
    }

    public String name() {
        return name;
    }

    public String color() {
        return color;
    }

    public long version() {
        return version;
    }
}
