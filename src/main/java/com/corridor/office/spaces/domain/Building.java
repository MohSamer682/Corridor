package com.corridor.office.spaces.domain;

import com.corridor.office.common.domain.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "buildings")
public class Building extends AuditedEntity {

    @Column(nullable = false, updatable = false)
    private UUID organizationId;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(length = 120)
    private String subtitle;

    // Where the building stands on the campus plan, in scene units.
    @Column(name = "campus_x", nullable = false, precision = 6, scale = 2)
    private BigDecimal campusX;

    @Column(name = "campus_z", nullable = false, precision = 6, scale = 2)
    private BigDecimal campusZ;

    @Column(nullable = false)
    private short sortOrder;

    @OneToMany(mappedBy = "building")
    @OrderBy("level")
    private List<Floor> floors = new ArrayList<>();

    protected Building() {}

    public UUID organizationId() {
        return organizationId;
    }

    public String name() {
        return name;
    }

    public String subtitle() {
        return subtitle;
    }

    public BigDecimal campusX() {
        return campusX;
    }

    public BigDecimal campusZ() {
        return campusZ;
    }

    public List<Floor> floors() {
        return List.copyOf(floors);
    }
}
