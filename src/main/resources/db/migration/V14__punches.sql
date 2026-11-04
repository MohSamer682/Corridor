-- At most one open punch per person: the unique constraint is what stops a double punch-in.
CREATE TABLE open_punches (
    id              UUID PRIMARY KEY,
    organization_id UUID        NOT NULL,
    user_id         UUID        NOT NULL,
    started_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_open_punches_organizations FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_open_punches_users FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uq_open_punches_user_id UNIQUE (user_id)
);

-- Finished intervals. user_id has no foreign key: attendance stays after someone is removed.
CREATE TABLE punches (
    id              UUID PRIMARY KEY,
    organization_id UUID        NOT NULL,
    user_id         UUID        NOT NULL,
    started_at      TIMESTAMPTZ NOT NULL,
    ended_at        TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_punches_organizations FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT ck_punches_interval CHECK (ended_at >= started_at)
);

CREATE INDEX idx_punches_user_started_at ON punches (organization_id, user_id, started_at DESC);
CREATE INDEX idx_punches_organization_started_at ON punches (organization_id, started_at);
