package com.corridor.office.workboard.application;

import com.corridor.office.workboard.application.JiraClient.Credentials;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Each company's Jira site and board, with the token decrypted only in memory. */
@Component
class JiraConnections {

    record Connection(
            UUID organizationId,
            UUID boardId,
            Credentials credentials,
            String projectKey,
            String webhookSecret,
            Instant connectedAt) {}

    private static final String SELECT =
            """
            SELECT c.organization_id, c.site_url, c.account_email, c.token_cipher, c.webhook_secret, c.connected_at,
                   b.id AS board_id, b.project_key
            FROM jira_connections c JOIN jira_boards b ON b.organization_id = c.organization_id""";

    private final JdbcClient jdbc;
    private final TokenCipher cipher;

    JiraConnections(JdbcClient jdbc, TokenCipher cipher) {
        this.jdbc = jdbc;
        this.cipher = cipher;
    }

    /** Saves or replaces the company's connection; a different project starts the board from empty. */
    UUID save(Connection connection, UUID connectedBy) {
        store(connection, connectedBy);
        return board(connection.organizationId(), connection.projectKey());
    }

    private void store(Connection connection, UUID connectedBy) {
        jdbc.sql(
                        """
                        INSERT INTO jira_connections (organization_id, site_url, account_email, token_cipher,
                                                      webhook_secret, connected_by, connected_at)
                        VALUES (:org, :site, :email, :token, :secret, :by, :at)
                        ON CONFLICT (organization_id) DO UPDATE SET site_url = EXCLUDED.site_url,
                            account_email = EXCLUDED.account_email, token_cipher = EXCLUDED.token_cipher,
                            webhook_secret = EXCLUDED.webhook_secret, connected_by = EXCLUDED.connected_by,
                            connected_at = EXCLUDED.connected_at""")
                .param("org", connection.organizationId())
                .param("site", connection.credentials().siteUrl())
                .param("email", connection.credentials().email())
                .param("token", cipher.encrypt(connection.credentials().apiToken()))
                .param("secret", connection.webhookSecret())
                .param("by", connectedBy)
                .param("at", Timestamp.from(connection.connectedAt()))
                .update();
    }

    private UUID board(UUID org, String projectKey) {
        jdbc.sql("DELETE FROM jira_boards WHERE organization_id = :org AND project_key <> :key")
                .param("org", org)
                .param("key", projectKey)
                .update();
        return jdbc.sql(
                        """
                        INSERT INTO jira_boards (id, organization_id, project_key) VALUES (:id, :org, :key)
                        ON CONFLICT (organization_id) DO UPDATE SET project_key = EXCLUDED.project_key
                        RETURNING id""")
                .param("id", UUID.randomUUID())
                .param("org", org)
                .param("key", projectKey)
                .query(UUID.class)
                .single();
    }

    Optional<Connection> find(UUID organizationId) {
        return jdbc.sql(SELECT + " WHERE c.organization_id = :org")
                .param("org", organizationId)
                .query((rs, row) -> row(rs))
                .optional();
    }

    List<Connection> all() {
        return jdbc.sql(SELECT).query((rs, row) -> row(rs)).list();
    }

    private Connection row(ResultSet rs) throws SQLException {
        return new Connection(
                rs.getObject("organization_id", UUID.class),
                rs.getObject("board_id", UUID.class),
                new Credentials(
                        rs.getString("site_url"),
                        rs.getString("account_email"),
                        cipher.decrypt(rs.getBytes("token_cipher"))),
                rs.getString("project_key"),
                rs.getString("webhook_secret"),
                rs.getTimestamp("connected_at").toInstant());
    }
}
