-- A retried request with the same key gets the first answer back instead of acting twice.
CREATE TABLE idempotency_keys (
    user_id    UUID        NOT NULL,
    key        UUID        NOT NULL,
    body       TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_idempotency_keys PRIMARY KEY (user_id, key)
);

CREATE INDEX idx_idempotency_keys_created_at ON idempotency_keys (created_at);
