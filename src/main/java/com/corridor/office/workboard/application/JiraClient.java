package com.corridor.office.workboard.application;

import java.time.Instant;
import java.util.List;

/** Reads a Jira project's issues. The office only reads; it never changes anything in Jira. */
public interface JiraClient {

    /** An Atlassian site, the account the token belongs to, and the API token. */
    record Credentials(String siteUrl, String email, String apiToken) {}

    /** One issue, already placed in a board column: To Do, In Progress, In Review or Done. */
    record JiraIssue(
            String key,
            String summary,
            String status,
            String column,
            String type,
            String priority,
            String assignee,
            Double storyPoints,
            Instant updated) {}

    /** Open issues plus anything finished in the last 14 days, most recently updated first. */
    List<JiraIssue> issues(Credentials credentials, String projectKey);

    /** Jira didn't answer, answered with an error, or refused the credentials. */
    class JiraUnreachableException extends RuntimeException {

        public JiraUnreachableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
