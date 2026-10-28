package com.corridor.office.spaces.domain;

import com.corridor.office.common.domain.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

/** A walled room or an open area of a floor; bounds are inclusive tile coordinates. */
@Entity
@Table(name = "rooms")
public class Room extends AuditedEntity {

    @Column(nullable = false, updatable = false)
    private UUID floorId;

    @Column(nullable = false, length = 40)
    private String key;

    @Column(nullable = false, length = 60)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RoomKind kind;

    @Column(name = "min_x", nullable = false)
    private short minX;

    @Column(name = "min_y", nullable = false)
    private short minY;

    @Column(name = "max_x", nullable = false)
    private short maxX;

    @Column(name = "max_y", nullable = false)
    private short maxY;

    @Column(name = "is_private", nullable = false)
    private boolean privateRoom;

    private UUID ownerId;

    protected Room() {}

    public UUID floorId() {
        return floorId;
    }

    public String key() {
        return key;
    }

    public String name() {
        return name;
    }

    public RoomKind kind() {
        return kind;
    }

    public int minX() {
        return minX;
    }

    public int minY() {
        return minY;
    }

    public int maxX() {
        return maxX;
    }

    public int maxY() {
        return maxY;
    }

    public boolean isPrivate() {
        return privateRoom;
    }

    public UUID ownerId() {
        return ownerId;
    }
}
