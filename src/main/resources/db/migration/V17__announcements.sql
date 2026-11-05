CREATE TABLE announcements (
    id              UUID PRIMARY KEY,
    organization_id UUID          NOT NULL,
    author_id       UUID          NOT NULL,
    team_id         UUID,
    body            VARCHAR(1000) NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL,
    CONSTRAINT fk_announcements_organizations FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_announcements_teams FOREIGN KEY (team_id) REFERENCES teams (id) ON DELETE CASCADE,
    CONSTRAINT ck_announcements_body CHECK (char_length(body) BETWEEN 1 AND 1000)
);

-- author_id has no foreign key: what someone announced stays after they are removed.
CREATE INDEX idx_announcements_organization_created_at ON announcements (organization_id, created_at DESC, id DESC);

-- Someone dismissed an announcement; anything without a row here is still shown to them.
CREATE TABLE announcement_reads (
    announcement_id UUID        NOT NULL,
    user_id         UUID        NOT NULL,
    read_at         TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_announcement_reads PRIMARY KEY (announcement_id, user_id),
    CONSTRAINT fk_announcement_reads_announcements FOREIGN KEY (announcement_id) REFERENCES announcements (id) ON DELETE CASCADE
);
