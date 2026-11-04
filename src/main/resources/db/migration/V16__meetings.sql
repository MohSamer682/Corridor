CREATE TABLE meetings (
    id               UUID PRIMARY KEY,
    organization_id  UUID         NOT NULL,
    room_id          UUID         NOT NULL,
    organizer_id     UUID         NOT NULL,
    title            VARCHAR(120) NOT NULL,
    starts_at        TIMESTAMPTZ  NOT NULL,
    ends_at          TIMESTAMPTZ  NOT NULL,
    scheduled_during TSTZRANGE    GENERATED ALWAYS AS (tstzrange(starts_at, ends_at, '[)')) STORED,
    status           VARCHAR(12)  NOT NULL,
    version          BIGINT       NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL,
    created_by       UUID,
    updated_at       TIMESTAMPTZ  NOT NULL,
    updated_by       UUID,
    CONSTRAINT fk_meetings_organizations FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_meetings_rooms FOREIGN KEY (room_id) REFERENCES rooms (id),
    CONSTRAINT ck_meetings_status CHECK (status IN ('SCHEDULED', 'LIVE', 'ENDED', 'CANCELLED')),
    CONSTRAINT ck_meetings_length CHECK (ends_at > starts_at),
    -- Two live or upcoming meetings can never share a room at the same time, however the requests race (ADR-007).
    CONSTRAINT ex_meetings_room_overlap EXCLUDE USING gist (room_id WITH =, scheduled_during WITH &&)
        WHERE (status IN ('SCHEDULED', 'LIVE'))
);

CREATE INDEX idx_meetings_organization_during ON meetings USING gist (organization_id, scheduled_during);

CREATE TABLE meeting_attendees (
    meeting_id UUID NOT NULL,
    user_id    UUID NOT NULL,
    CONSTRAINT pk_meeting_attendees PRIMARY KEY (meeting_id, user_id),
    CONSTRAINT fk_meeting_attendees_meetings FOREIGN KEY (meeting_id) REFERENCES meetings (id) ON DELETE CASCADE
);

CREATE INDEX idx_meeting_attendees_user_id ON meeting_attendees (user_id);
