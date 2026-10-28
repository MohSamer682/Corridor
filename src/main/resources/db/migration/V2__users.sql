CREATE TABLE users (
    id              UUID PRIMARY KEY,
    organization_id UUID         NOT NULL,
    email           VARCHAR(254) NOT NULL,
    display_name    VARCHAR(80)  NOT NULL,
    job_title       VARCHAR(80),
    role            VARCHAR(16)  NOT NULL,
    status          VARCHAR(16)  NOT NULL,
    password_hash   VARCHAR(255),
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL,
    created_by      UUID,
    updated_at      TIMESTAMPTZ  NOT NULL,
    updated_by      UUID,
    CONSTRAINT fk_users_organizations FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_email_lowercase CHECK (email = lower(email)),
    CONSTRAINT ck_users_role CHECK (role IN ('ADMIN', 'MANAGER', 'EMPLOYEE')),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'SUSPENDED'))
);

CREATE INDEX idx_users_organization_id_status ON users (organization_id, status);
