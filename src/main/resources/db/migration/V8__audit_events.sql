CREATE TABLE audit_events (
    id              UUID PRIMARY KEY,
    organization_id UUID        NOT NULL,
    sequence        BIGINT      NOT NULL,
    actor_id        UUID,
    action          VARCHAR(60) NOT NULL,
    target_type     VARCHAR(40) NOT NULL,
    target_id       UUID,
    details         JSONB       NOT NULL DEFAULT '{}',
    previous_hash   BYTEA,
    hash            BYTEA       NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_audit_events_organizations FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT uq_audit_events_organization_id_sequence UNIQUE (organization_id, sequence),
    CONSTRAINT ck_audit_events_sequence CHECK (sequence > 0)
);

-- actor_id has no foreign key: people can be removed, and their past actions must stay as written.
CREATE INDEX idx_audit_events_organization_id_created_at ON audit_events (organization_id, created_at DESC, id DESC);
CREATE INDEX idx_audit_events_actor_id ON audit_events (actor_id);

CREATE FUNCTION refuse_audit_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_events is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_events_append_only
    BEFORE UPDATE OR DELETE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION refuse_audit_change();
