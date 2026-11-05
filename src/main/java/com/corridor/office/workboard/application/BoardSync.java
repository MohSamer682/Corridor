package com.corridor.office.workboard.application;

import com.corridor.office.workboard.application.JiraClient.JiraIssue;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes Jira's issues into the cache. An issue only changes when Jira's copy is newer than the cached one, so a late
 * webhook can't undo a poll that already saw a later change.
 */
@Component
class BoardSync {

    private static final String UPSERT =
            """
            INSERT INTO jira_issues (board_id, issue_key, summary, status, column_name, issue_type, priority,
                                     assignee, story_points, updated_at)
            VALUES (:board, :key, :summary, :status, :column, :type, :priority, :assignee, :points, :updated)
            ON CONFLICT (board_id, issue_key) DO UPDATE SET summary = EXCLUDED.summary, status = EXCLUDED.status,
                column_name = EXCLUDED.column_name, issue_type = EXCLUDED.issue_type, priority = EXCLUDED.priority,
                assignee = EXCLUDED.assignee, story_points = EXCLUDED.story_points, updated_at = EXCLUDED.updated_at
            WHERE jira_issues.updated_at < EXCLUDED.updated_at""";

    private final JdbcClient jdbc;

    BoardSync(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** A full read from Jira: newer issues are written, issues Jira no longer lists are dropped. */
    @Transactional
    void replace(UUID boardId, List<JiraIssue> issues, Instant now) {
        issues.forEach(issue -> apply(boardId, issue));
        jdbc.sql("DELETE FROM jira_issues WHERE board_id = :board AND issue_key <> ALL(:keys)")
                .param("board", boardId)
                .param("keys", issues.stream().map(JiraIssue::key).toArray(String[]::new))
                .update();
        jdbc.sql("UPDATE jira_boards SET synced_at = :now, failed_at = NULL WHERE id = :board")
                .param("now", Timestamp.from(now))
                .param("board", boardId)
                .update();
    }

    void apply(UUID boardId, JiraIssue issue) {
        jdbc.sql(UPSERT)
                .param("board", boardId)
                .param("key", issue.key())
                .param("summary", clip(issue.summary(), 500))
                .param("status", clip(issue.status(), 80))
                .param("column", issue.column())
                .param("type", issue.type())
                .param("priority", issue.priority())
                .param("assignee", issue.assignee())
                .param("points", issue.storyPoints())
                .param("updated", Timestamp.from(issue.updated()))
                .update();
    }

    void remove(UUID boardId, String issueKey) {
        jdbc.sql("DELETE FROM jira_issues WHERE board_id = :board AND issue_key = :key")
                .param("board", boardId)
                .param("key", issueKey)
                .update();
    }

    /** Jira didn't answer; the board keeps its last copy and shows how old it is. */
    void failed(UUID boardId, Instant now) {
        jdbc.sql("UPDATE jira_boards SET failed_at = :now WHERE id = :board")
                .param("now", Timestamp.from(now))
                .param("board", boardId)
                .update();
    }

    private static String clip(String text, int max) {
        return text.length() > max ? text.substring(0, max) : text;
    }
}
