CREATE TABLE refresh_tokens (
    id             UUID PRIMARY KEY,
    user_id        UUID        NOT NULL,
    family_id      UUID        NOT NULL,
    token_hash     BYTEA       NOT NULL,
    expires_at     TIMESTAMPTZ NOT NULL,
    revoked_at     TIMESTAMPTZ,
    replaced_by_id UUID,
    created_at     TIMESTAMPTZ NOT NULL,
    created_by     UUID,
    updated_at     TIMESTAMPTZ NOT NULL,
    updated_by     UUID,
    CONSTRAINT fk_refresh_tokens_users FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_refresh_tokens_replaced_by FOREIGN KEY (replaced_by_id) REFERENCES refresh_tokens (id),
    CONSTRAINT uq_refresh_tokens_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_family_id ON refresh_tokens (family_id);
