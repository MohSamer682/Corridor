-- One Jira site per company. The API token is encrypted with AES-GCM before it is stored; the key is not in the database.
CREATE TABLE jira_connections (
    organization_id UUID         PRIMARY KEY,
    site_url        VARCHAR(255) NOT NULL,
    account_email   VARCHAR(255) NOT NULL,
    token_cipher    BYTEA        NOT NULL,
    webhook_secret  VARCHAR(64)  NOT NULL,
    connected_by    UUID         NOT NULL,
    connected_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_jira_connections_organizations FOREIGN KEY (organization_id) REFERENCES organizations (id)
);

-- The project shown on every floor's sprint board. synced_at is the last good copy; failed_at the last failed refresh.
CREATE TABLE jira_boards (
    id              UUID        PRIMARY KEY,
    organization_id UUID        NOT NULL,
    project_key     VARCHAR(20) NOT NULL,
    synced_at       TIMESTAMPTZ,
    failed_at       TIMESTAMPTZ,
    CONSTRAINT fk_jira_boards_organizations FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT uq_jira_boards_organization_id UNIQUE (organization_id),
    CONSTRAINT ck_jira_boards_project_key CHECK (project_key ~ '^[A-Z][A-Z0-9_]{1,19}$')
);
