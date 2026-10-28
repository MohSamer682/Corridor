package com.corridor.office.spaces.domain;

import com.corridor.office.common.domain.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "floors")
public class Floor extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "building_id", nullable = false, updatable = false)
    private Building building;

    @Column(nullable = false)
    private short level;

    @Column(nullable = false, length = 8)
    private String code;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(length = 200)
    private String details;

    @Column(nullable = false)
    private int currentLayoutVersion;

    protected Floor() {}

    public Building building() {
        return building;
    }

    public int level() {
        return level;
    }

    public String code() {
        return code;
    }

    public String name() {
        return name;
    }

    public String details() {
        return details;
    }

    public int currentLayoutVersion() {
        return currentLayoutVersion;
    }
}
