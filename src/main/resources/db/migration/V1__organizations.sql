CREATE TABLE organizations (
    id             UUID PRIMARY KEY,
    name           VARCHAR(120) NOT NULL,
    slug           VARCHAR(60)  NOT NULL,
    time_zone      VARCHAR(40)  NOT NULL DEFAULT 'Africa/Cairo',
    weekend_days   SMALLINT[]   NOT NULL DEFAULT '{5,6}',
    work_starts_at TIME         NOT NULL DEFAULT '09:00',
    created_at     TIMESTAMPTZ  NOT NULL,
    created_by     UUID,
    updated_at     TIMESTAMPTZ  NOT NULL,
    updated_by     UUID,
    CONSTRAINT uq_organizations_slug UNIQUE (slug)
);
