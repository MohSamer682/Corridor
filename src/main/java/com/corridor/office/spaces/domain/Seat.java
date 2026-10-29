package com.corridor.office.spaces.domain;

import com.corridor.office.common.domain.AuditedEntity;
import com.corridor.office.spaces.domain.TileGrid.Tile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "seats")
public class Seat extends AuditedEntity {

    @Column(nullable = false, updatable = false)
    private UUID floorId;

    private UUID roomId;

    @Column(nullable = false)
    private short x;

    @Column(nullable = false)
    private short y;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 1)
    private Facing facing;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private SeatKind kind;

    private UUID assignedUserId;

    protected Seat() {}

    public UUID floorId() {
        return floorId;
    }

    public UUID roomId() {
        return roomId;
    }

    public Tile tile() {
        return new Tile(x, y);
    }

    public Facing facing() {
        return facing;
    }

    public SeatKind kind() {
        return kind;
    }

    public UUID assignedUserId() {
        return assignedUserId;
    }
}
