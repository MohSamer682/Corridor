package com.corridor.office.spaces.domain;

import com.corridor.office.spaces.domain.TileGrid.Tile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/** One version of the grid and decor of a floor; rows never change, a new layout is a new version. */
@Entity
@Table(name = "floor_layouts")
public class FloorLayout {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID floorId;

    @Column(nullable = false, updatable = false)
    private int version;

    @Column(nullable = false, updatable = false)
    private short width;

    @Column(nullable = false, updatable = false)
    private short height;

    @Column(name = "arrival_x", nullable = false, updatable = false)
    private short arrivalX;

    @Column(name = "arrival_y", nullable = false, updatable = false)
    private short arrivalY;

    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String tiles;

    // Decor the 3D view draws (furniture, rugs, screens, hot spots); the server only passes it through.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    private Map<String, Object> furniture;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected FloorLayout() {}

    public TileGrid grid() {
        return TileGrid.parse(width, height, tiles, new Tile(arrivalX, arrivalY));
    }

    public UUID floorId() {
        return floorId;
    }

    public int version() {
        return version;
    }

    public String tiles() {
        return tiles;
    }

    public Map<String, Object> furniture() {
        return furniture;
    }
}
