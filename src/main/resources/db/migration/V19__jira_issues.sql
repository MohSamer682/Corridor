-- The cached copy of a board's issues. The office reads only this table; it never calls Jira while a page loads.
CREATE TABLE jira_issues (
    board_id     UUID          NOT NULL,
    issue_key    VARCHAR(32)   NOT NULL,
    summary      VARCHAR(500)  NOT NULL,
    status       VARCHAR(80)   NOT NULL,
    column_name  VARCHAR(20)   NOT NULL,
    issue_type   VARCHAR(80),
    priority     VARCHAR(40),
    assignee     VARCHAR(200),
    story_points NUMERIC(6, 1),
    updated_at   TIMESTAMPTZ   NOT NULL,
    CONSTRAINT pk_jira_issues PRIMARY KEY (board_id, issue_key),
    CONSTRAINT fk_jira_issues_jira_boards FOREIGN KEY (board_id) REFERENCES jira_boards (id) ON DELETE CASCADE,
    CONSTRAINT ck_jira_issues_column_name CHECK (column_name IN ('To Do', 'In Progress', 'In Review', 'Done'))
);
