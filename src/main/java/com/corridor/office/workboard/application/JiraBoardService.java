package com.corridor.office.workboard.application;

import com.corridor.office.common.domain.ForbiddenException;
import com.corridor.office.common.domain.InvalidInputException;
import com.corridor.office.common.security.CurrentUser;
import com.corridor.office.common.security.Role;
import com.corridor.office.workboard.application.JiraClient.Credentials;
import com.corridor.office.workboard.application.JiraClient.JiraUnreachableException;
import com.corridor.office.workboard.application.JiraConnections.Connection;
import com.corridor.office.workboard.events.JiraConnected;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Connects a company's Jira project and keeps its cached board fresh: a poll every 5 minutes, plus webhooks. */
@Service
public class JiraBoardService {

    /** What an admin fills in. The token is write-only: it never comes back out. */
    public record Connect(String siteUrl, String email, String apiToken, String projectKey) {}

    public record ConnectionView(
            String siteUrl,
            String email,
            String projectKey,
            String webhookPath,
            String webhookSecret,
            Instant connectedAt) {}

    private static final Logger LOG = LoggerFactory.getLogger(JiraBoardService.class);
    // Only Atlassian Cloud sites: the server never calls an address an admin typed freely.
    private static final Pattern SITE = Pattern.compile("^https://[a-z0-9][a-z0-9-]{0,62}\\.atlassian\\.net$");
    private static final Pattern PROJECT = Pattern.compile("^[A-Z][A-Z0-9_]{1,19}$");

    private final JiraConnections connections;
    private final JiraClient jira;
    private final BoardSync sync;
    private final BoardQuery query;
    private final WorkboardEvents events;
    private final SecureRandom random = new SecureRandom();

    JiraBoardService(
            JiraConnections connections, JiraClient jira, BoardSync sync, BoardQuery query, WorkboardEvents events) {
        this.connections = connections;
        this.jira = jira;
        this.sync = sync;
        this.query = query;
        this.events = events;
    }

    /** Checks the credentials by reading the project once, then stores them and that first copy of the board. */
    @Transactional
    public ConnectionView connect(CurrentUser admin, Connect request) {
        adminOnly(admin);
        var credentials = credentials(request);
        var projectKey = projectKey(request.projectKey());
        var issues = read(credentials, projectKey);
        var now = events.clock().instant();
        var connection = new Connection(admin.organizationId(), null, credentials, projectKey, secret(), now);
        var boardId = connections.save(connection, admin.userId());
        sync.replace(boardId, issues, now);
        events.publisher()
                .publishEvent(
                        new JiraConnected(admin.organizationId(), admin.userId(), credentials.siteUrl(), projectKey));
        return view(connection);
    }

    @Transactional(readOnly = true)
    public Optional<ConnectionView> connection(CurrentUser admin) {
        adminOnly(admin);
        return connections.find(admin.organizationId()).map(JiraBoardService::view);
    }

    /** The cached board for everyone in the company; empty when Jira isn't connected. */
    @Transactional(readOnly = true)
    public Optional<BoardQuery.BoardView> board(CurrentUser viewer) {
        return query.board(viewer.organizationId());
    }

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT1M")
    public void refreshAll() {
        for (var connection : connections.all()) {
            var now = events.clock().instant();
            try {
                sync.replace(connection.boardId(), jira.issues(connection.credentials(), connection.projectKey()), now);
            } catch (JiraUnreachableException e) {
                LOG.warn("Jira board {} kept its last copy: {}", connection.boardId(), e.getMessage());
                sync.failed(connection.boardId(), now);
            }
        }
    }

    private List<JiraClient.JiraIssue> read(Credentials credentials, String projectKey) {
        try {
            return jira.issues(credentials, projectKey);
        } catch (JiraUnreachableException e) {
            throw new InvalidInputException("jira.refused", e.getMessage());
        }
    }

    private static void adminOnly(CurrentUser person) {
        if (person.role() != Role.ADMIN) {
            throw new ForbiddenException("jira.adminsOnly");
        }
    }

    private static Credentials credentials(Connect request) {
        var site = request.siteUrl() == null ? "" : request.siteUrl().strip().toLowerCase(Locale.ROOT);
        site = site.endsWith("/") ? site.substring(0, site.length() - 1) : site;
        if (!SITE.matcher(site).matches()) {
            throw new InvalidInputException("jira.siteUrl");
        }
        if (blank(request.email()) || blank(request.apiToken())) {
            throw new InvalidInputException("jira.fields");
        }
        return new Credentials(site, request.email().strip(), request.apiToken().strip());
    }

    private static String projectKey(String key) {
        var upper = key == null ? "" : key.strip().toUpperCase(Locale.ROOT);
        if (!PROJECT.matcher(upper).matches()) {
            throw new InvalidInputException("jira.projectKey");
        }
        return upper;
    }

    private static boolean blank(String text) {
        return text == null || text.isBlank();
    }

    private String secret() {
        var bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static ConnectionView view(Connection connection) {
        return new ConnectionView(
                connection.credentials().siteUrl(),
                connection.credentials().email(),
                connection.projectKey(),
                "/api/v1/integrations/jira/webhook/" + connection.organizationId(),
                connection.webhookSecret(),
                connection.connectedAt());
    }
}
