package com.corridor.office.workboard.application;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Reads the cached board. It never calls Jira, so the board opens at once even while Jira is down. */
@Component
public class BoardQuery {

    public static final List<String> COLUMNS = List.of("To Do", "In Progress", "In Review", "Done");

    /** {@code stale} when the last refresh failed or the copy is older than three polls. */
    public record BoardView(String projectKey, String siteUrl, List<Column> columns, Instant syncedAt, boolean stale) {}

    public record Column(String name, List<Card> cards) {}

    public record Card(
            String key,
            String summary,
            String status,
            String type,
            String priority,
            String assignee,
            Double storyPoints,
            String url) {}

    private record Board(UUID id, String projectKey, String siteUrl, Instant syncedAt, Instant failedAt) {}

    private static final Duration FRESH = Duration.ofMinutes(15);

    private final JdbcClient jdbc;
    private final Clock clock;

    BoardQuery(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    Optional<BoardView> board(UUID organizationId) {
        return jdbc.sql(
                        """
                        SELECT b.id, b.project_key, b.synced_at, b.failed_at, c.site_url
                        FROM jira_boards b JOIN jira_connections c ON c.organization_id = b.organization_id
                        WHERE b.organization_id = :org""")
                .param("org", organizationId)
                .query((rs, row) -> new Board(
                        rs.getObject("id", UUID.class),
                        rs.getString("project_key"),
                        rs.getString("site_url"),
                        instant(rs, "synced_at"),
                        instant(rs, "failed_at")))
                .optional()
                .map(this::view);
    }

    private BoardView view(Board board) {
        var columns = new LinkedHashMap<String, List<Card>>();
        COLUMNS.forEach(name -> columns.put(name, new ArrayList<>()));
        jdbc.sql("SELECT * FROM jira_issues WHERE board_id = :board ORDER BY updated_at DESC, issue_key")
                .param("board", board.id())
                .query((rs, row) -> {
                    columns.get(rs.getString("column_name")).add(card(rs, board.siteUrl()));
                    return null;
                })
                .list();
        var stale = board.syncedAt() == null
                || board.failedAt() != null
                || board.syncedAt().isBefore(clock.instant().minus(FRESH));
        return new BoardView(
                board.projectKey(),
                board.siteUrl(),
                columns.entrySet().stream()
                        .map(e -> new Column(e.getKey(), e.getValue()))
                        .toList(),
                board.syncedAt(),
                stale);
    }

    private static Card card(ResultSet rs, String siteUrl) throws SQLException {
        var points = rs.getBigDecimal("story_points");
        var key = rs.getString("issue_key");
        return new Card(
                key,
                rs.getString("summary"),
                rs.getString("status"),
                rs.getString("issue_type"),
                rs.getString("priority"),
                rs.getString("assignee"),
                points == null ? null : points.doubleValue(),
                siteUrl + "/browse/" + key);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        var at = rs.getTimestamp(column);
        return at == null ? null : at.toInstant();
    }
}
