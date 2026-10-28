ALTER TABLE users ADD COLUMN team_id UUID;

ALTER TABLE users ADD CONSTRAINT fk_users_teams FOREIGN KEY (team_id) REFERENCES teams (id);

CREATE INDEX idx_users_team_id ON users (team_id);
