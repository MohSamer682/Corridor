-- Append-only: a changed layout is a new version, and floors.current_layout_version points at the live one.
CREATE TABLE floor_layouts (
    id         UUID PRIMARY KEY,
    floor_id   UUID        NOT NULL,
    version    INT         NOT NULL,
    width      SMALLINT    NOT NULL,
    height     SMALLINT    NOT NULL,
    arrival_x  SMALLINT    NOT NULL,
    arrival_y  SMALLINT    NOT NULL,
    tiles      TEXT        NOT NULL,
    furniture  JSONB       NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    CONSTRAINT fk_floor_layouts_floors FOREIGN KEY (floor_id) REFERENCES floors (id),
    CONSTRAINT uq_floor_layouts_floor_id_version UNIQUE (floor_id, version),
    CONSTRAINT ck_floor_layouts_size CHECK (width > 0 AND height > 0 AND length(tiles) = width * height),
    CONSTRAINT ck_floor_layouts_arrival CHECK (arrival_x BETWEEN 0 AND width - 1 AND arrival_y BETWEEN 0 AND height - 1)
);
