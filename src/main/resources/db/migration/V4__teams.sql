CREATE TABLE teams (
    id              UUID PRIMARY KEY,
    organization_id UUID        NOT NULL,
    name            VARCHAR(60) NOT NULL,
    color           CHAR(7)     NOT NULL,
    version         BIGINT      NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL,
    created_by      UUID,
    updated_at      TIMESTAMPTZ NOT NULL,
    updated_by      UUID,
    CONSTRAINT fk_teams_organizations FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT ck_teams_color CHECK (color ~ '^#[0-9A-F]{6}$')
);

CREATE UNIQUE INDEX uq_teams_organization_id_name ON teams (organization_id, lower(name));
