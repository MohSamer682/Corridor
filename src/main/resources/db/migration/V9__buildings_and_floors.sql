CREATE TABLE buildings (
    id              UUID PRIMARY KEY,
    organization_id UUID          NOT NULL,
    name            VARCHAR(80)   NOT NULL,
    subtitle        VARCHAR(120),
    campus_x        NUMERIC(6, 2) NOT NULL,
    campus_z        NUMERIC(6, 2) NOT NULL,
    sort_order      SMALLINT      NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL,
    created_by      UUID,
    updated_at      TIMESTAMPTZ   NOT NULL,
    updated_by      UUID,
    CONSTRAINT fk_buildings_organizations FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT uq_buildings_organization_id_name UNIQUE (organization_id, name)
);

CREATE TABLE floors (
    id                     UUID PRIMARY KEY,
    building_id            UUID        NOT NULL,
    level                  SMALLINT    NOT NULL,
    code                   VARCHAR(8)  NOT NULL,
    name                   VARCHAR(80) NOT NULL,
    details                VARCHAR(200),
    current_layout_version INT         NOT NULL,
    created_at             TIMESTAMPTZ NOT NULL,
    created_by             UUID,
    updated_at             TIMESTAMPTZ NOT NULL,
    updated_by             UUID,
    CONSTRAINT fk_floors_buildings FOREIGN KEY (building_id) REFERENCES buildings (id),
    CONSTRAINT uq_floors_building_id_level UNIQUE (building_id, level)
);
