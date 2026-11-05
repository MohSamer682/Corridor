package com.corridor.office.workboard.application;

import com.corridor.office.workboard.application.JiraClient.JiraIssue;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import tools.jackson.databind.JsonNode;

/** Turns one issue as Jira sends it (search results and webhooks alike) into a board card. */
public final class JiraIssues {

    // Jira writes offsets without a colon: 2026-11-05T09:12:00.000+0200.
    private static final DateTimeFormatter UPDATED = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    // Story points live in a custom field; this is its id on Jira Cloud's software projects.
    private static final String STORY_POINTS = "customfield_10016";

    private JiraIssues() {}

    public static JiraIssue read(JsonNode issue) {
        var fields = issue.path("fields");
        var status = fields.path("status");
        var points = fields.path(STORY_POINTS);
        return new JiraIssue(
                issue.path("key").asString(),
                fields.path("summary").asString(),
                status.path("name").asString(),
                column(
                        status.path("statusCategory").path("key").asString(),
                        status.path("name").asString()),
                text(fields.path("issuetype").path("name")),
                text(fields.path("priority").path("name")),
                text(fields.path("assignee").path("displayName")),
                points.isNumber() ? points.asDouble() : null,
                OffsetDateTime.parse(fields.path("updated").asString(), UPDATED).toInstant());
    }

    /** Jira groups every workflow status into new, indeterminate or done; review statuses get their own column. */
    static String column(String category, String status) {
        return switch (category) {
            case "new" -> "To Do";
            case "done" -> "Done";
            default -> status.toLowerCase(Locale.ROOT).contains("review") ? "In Review" : "In Progress";
        };
    }

    private static String text(JsonNode node) {
        return node.isMissingNode() || node.isNull() ? null : node.asString();
    }
}
