package com.corridor.office.workboard.application;

import com.corridor.office.common.domain.InvalidInputException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Issue events Jira pushes the moment something changes. Jira signs each body with the shared secret
 * ({@code X-Hub-Signature: sha256=<hex HMAC>}); anything unsigned or signed with another secret is refused.
 */
@Component
public class JiraWebhook {

    private final JiraConnections connections;
    private final BoardSync sync;
    private final JsonMapper json;

    JiraWebhook(JiraConnections connections, BoardSync sync, JsonMapper json) {
        this.connections = connections;
        this.sync = sync;
        this.json = json;
    }

    /** False when the signature doesn't match, so the caller answers 401. */
    @Transactional
    public boolean receive(UUID organizationId, String signature, String body) {
        var connection = connections.find(organizationId).orElse(null);
        if (connection == null || !signed(connection.webhookSecret(), signature, body)) {
            return false;
        }
        try {
            var event = json.readTree(body);
            var issue = event.path("issue");
            var key = issue.path("key").asString();
            if (!key.startsWith(connection.projectKey() + "-")) {
                return true;
            }
            if ("jira:issue_deleted".equals(event.path("webhookEvent").asString())) {
                sync.remove(connection.boardId(), key);
            } else {
                sync.apply(connection.boardId(), JiraIssues.read(issue));
            }
            return true;
        } catch (RuntimeException e) {
            // Unreadable JSON, a missing field or a date Jira never sends; nothing is written.
            throw new InvalidInputException("jira.badEvent");
        }
    }

    private static boolean signed(String secret, String signature, String body) {
        if (signature == null || !signature.startsWith("sha256=")) {
            return false;
        }
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            var expected = HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.US_ASCII),
                    signature.substring("sha256=".length()).getBytes(StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is missing from this JVM", e);
        }
    }
}
