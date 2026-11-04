CREATE TABLE messages (
    id              UUID PRIMARY KEY,
    organization_id UUID          NOT NULL,
    floor_id        UUID          NOT NULL,
    sender_id       UUID          NOT NULL,
    recipients      UUID[]        NOT NULL,
    body            VARCHAR(2000) NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL,
    CONSTRAINT fk_messages_organizations FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_messages_floors FOREIGN KEY (floor_id) REFERENCES floors (id),
    CONSTRAINT ck_messages_body CHECK (char_length(body) BETWEEN 1 AND 2000)
);

-- sender_id has no foreign key: a removed person's messages stay until retention deletes them.
CREATE INDEX idx_messages_floor_created_at ON messages (organization_id, floor_id, created_at DESC, id DESC);
CREATE INDEX idx_messages_recipients ON messages USING gin (recipients);
CREATE INDEX idx_messages_created_at ON messages (created_at);
