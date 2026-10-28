CREATE TABLE rooms (
    id         UUID PRIMARY KEY,
    floor_id   UUID        NOT NULL,
    key        VARCHAR(40) NOT NULL,
    name       VARCHAR(60) NOT NULL,
    kind       VARCHAR(16) NOT NULL,
    min_x      SMALLINT    NOT NULL,
    min_y      SMALLINT    NOT NULL,
    max_x      SMALLINT    NOT NULL,
    max_y      SMALLINT    NOT NULL,
    is_private BOOLEAN     NOT NULL,
    owner_id   UUID,
    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by UUID,
    CONSTRAINT fk_rooms_floors FOREIGN KEY (floor_id) REFERENCES floors (id),
    CONSTRAINT fk_rooms_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT uq_rooms_floor_id_key UNIQUE (floor_id, key),
    CONSTRAINT uq_rooms_floor_id_name UNIQUE (floor_id, name),
    CONSTRAINT uq_rooms_owner_id UNIQUE (owner_id),
    CONSTRAINT ck_rooms_kind CHECK (kind IN ('OFFICE', 'MEETING', 'CONFERENCE', 'HALL', 'BOOTH', 'SOCIAL', 'LAB', 'ZONE')),
    CONSTRAINT ck_rooms_bounds CHECK (min_x <= max_x AND min_y <= max_y)
);

CREATE TABLE seats (
    id               UUID PRIMARY KEY,
    floor_id         UUID        NOT NULL,
    room_id          UUID,
    x                SMALLINT    NOT NULL,
    y                SMALLINT    NOT NULL,
    facing           CHAR(1)     NOT NULL,
    kind             VARCHAR(12) NOT NULL,
    assigned_user_id UUID,
    created_at       TIMESTAMPTZ NOT NULL,
    created_by       UUID,
    updated_at       TIMESTAMPTZ NOT NULL,
    updated_by       UUID,
    CONSTRAINT fk_seats_floors FOREIGN KEY (floor_id) REFERENCES floors (id),
    CONSTRAINT fk_seats_rooms FOREIGN KEY (room_id) REFERENCES rooms (id),
    CONSTRAINT fk_seats_assigned_user FOREIGN KEY (assigned_user_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT uq_seats_floor_id_x_y UNIQUE (floor_id, x, y),
    CONSTRAINT uq_seats_assigned_user_id UNIQUE (assigned_user_id),
    CONSTRAINT ck_seats_facing CHECK (facing IN ('N', 'S', 'E', 'W')),
    CONSTRAINT ck_seats_kind CHECK (kind IN ('DESK', 'CHAIR', 'STOOL', 'SOFA', 'THEATRE'))
);

CREATE INDEX idx_buildings_organization_id ON buildings (organization_id);
CREATE INDEX idx_floors_building_id ON floors (building_id);
CREATE INDEX idx_rooms_floor_id ON rooms (floor_id);
CREATE INDEX idx_seats_room_id ON seats (room_id);
