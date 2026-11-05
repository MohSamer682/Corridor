package com.corridor.office.workboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.corridor.office.TestcontainersConfiguration;
import com.corridor.office.common.domain.ForbiddenException;
import com.corridor.office.common.domain.InvalidInputException;
import com.corridor.office.common.security.CurrentUser;
import com.corridor.office.identity.infrastructure.UserRepository;
import com.corridor.office.workboard.application.JiraBoardService;
import com.corridor.office.workboard.application.JiraBoardService.Connect;
import com.corridor.office.workboard.application.JiraClient;
import com.corridor.office.workboard.application.JiraClient.JiraIssue;
import com.corridor.office.workboard.application.JiraClient.JiraUnreachableException;
import com.corridor.office.workboard.application.JiraWebhook;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
        properties = {
            "spring.flyway.locations=classpath:db/migration,classpath:db/seed",
            "spring.flyway.out-of-order=true"
        })
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class WorkboardIntegrationTest {

    private static final Instant MORNING = Instant.parse("2026-11-05T08:00:00Z");

    @MockitoBean
    JiraClient jira;

    @Autowired
    JiraBoardService boards;

    @Autowired
    JiraWebhook webhook;

    @Autowired
    UserRepository users;

    @Autowired
    JdbcClient jdbc;

    private CurrentUser admin;
    private CurrentUser nour;

    private CurrentUser person(String email) {
        var u = users.findByEmail(email).orElseThrow();
        return new CurrentUser(u.id(), u.organization().id(), u.role());
    }

    private static JiraIssue issue(String key, String column, Instant updated) {
        return new JiraIssue(key, "Work on " + key, column, column, "Story", "High", "Nour Hassan", 3.0, updated);
    }

    private static Connect corridor() {
        return new Connect("https://corridor.atlassian.net", "admin@corridor.local", "secret-token", "COR");
    }

    @BeforeEach
    void forgetJira() {
        admin = person("admin@corridor.local");
        nour = person("nour@corridor.local");
        jdbc.sql("DELETE FROM jira_connections").update();
        jdbc.sql("DELETE FROM jira_boards").update();
    }

    @Test
    void shouldConnectAndCacheTheBoardWithTheTokenEncrypted() {
        when(jira.issues(any(), eq("COR")))
                .thenReturn(List.of(issue("COR-1", "To Do", MORNING), issue("COR-2", "In Review", MORNING)));

        var connection = boards.connect(admin, corridor());

        assertThat(connection.projectKey()).isEqualTo("COR");
        assertThat(connection.webhookSecret()).hasSizeGreaterThan(30);
        var board = boards.board(nour).orElseThrow();
        assertThat(board.stale()).isFalse();
        assertThat(board.columns())
                .extracting(c -> c.name() + " " + c.cards().size())
                .containsExactly("To Do 1", "In Progress 0", "In Review 1", "Done 0");
        assertThat(board.columns().getFirst().cards().getFirst().url())
                .isEqualTo("https://corridor.atlassian.net/browse/COR-1");
        var stored = jdbc.sql("SELECT token_cipher FROM jira_connections")
                .query(byte[].class)
                .single();
        assertThat(new String(stored, StandardCharsets.ISO_8859_1)).doesNotContain("secret-token");
        assertThat(jdbc.sql("SELECT count(*) FROM audit_events WHERE action = 'integration.jira_connected'")
                        .query(Long.class)
                        .single())
                .isPositive();
    }

    @Test
    void shouldLetOnlyAdminsConnect() {
        assertThatThrownBy(() -> boards.connect(nour, corridor())).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void shouldOnlyTalkToAtlassianSites() {
        var elsewhere = new Connect("http://169.254.169.254", "a@b.c", "t", "COR");

        assertThatThrownBy(() -> boards.connect(admin, elsewhere)).isInstanceOf(InvalidInputException.class);
    }

    @Test
    void shouldStoreNothingWhenJiraRefusesTheToken() {
        when(jira.issues(any(), any())).thenThrow(new JiraUnreachableException("Jira answered 401", null));

        assertThatThrownBy(() -> boards.connect(admin, corridor())).isInstanceOf(InvalidInputException.class);
        assertThat(boards.connection(admin)).isEmpty();
    }

    @Test
    void shouldKeepShowingTheLastCopyWhenJiraIsDown() {
        when(jira.issues(any(), any())).thenReturn(List.of(issue("COR-1", "To Do", MORNING)));
        boards.connect(admin, corridor());
        when(jira.issues(any(), any())).thenThrow(new JiraUnreachableException("Jira didn't answer", null));

        boards.refreshAll();

        var board = boards.board(nour).orElseThrow();
        assertThat(board.stale()).isTrue();
        assertThat(board.columns().getFirst().cards()).hasSize(1);
    }

    @Test
    void shouldIgnoreAWebhookOlderThanWhatIsCached() throws Exception {
        when(jira.issues(any(), any())).thenReturn(List.of(issue("COR-1", "In Progress", MORNING)));
        var secret = boards.connect(admin, corridor()).webhookSecret();

        var older = event("COR-1", "To Do", "new", "2026-11-05T07:00:00.000+0000");
        var newer = event("COR-1", "Done", "done", "2026-11-05T09:00:00.000+0000");

        assertThat(webhook.receive(admin.organizationId(), sign(secret, older), older))
                .isTrue();
        assertThat(column(nour, "COR-1")).isEqualTo("In Progress");
        assertThat(webhook.receive(admin.organizationId(), sign(secret, newer), newer))
                .isTrue();
        assertThat(column(nour, "COR-1")).isEqualTo("Done");
    }

    @Test
    void shouldRefuseAWebhookWithoutTheSecret() {
        when(jira.issues(any(), any())).thenReturn(List.of());
        boards.connect(admin, corridor());
        var body = event("COR-9", "To Do", "new", "2026-11-05T09:00:00.000+0000");

        assertThat(webhook.receive(admin.organizationId(), null, body)).isFalse();
        assertThat(webhook.receive(admin.organizationId(), "sha256=00", body)).isFalse();
        assertThat(boards.board(nour).orElseThrow().columns().getFirst().cards())
                .isEmpty();
    }

    private String column(CurrentUser viewer, String key) {
        return boards.board(viewer).orElseThrow().columns().stream()
                .filter(c -> c.cards().stream().anyMatch(card -> card.key().equals(key)))
                .findFirst()
                .orElseThrow()
                .name();
    }

    private static String event(String key, String status, String category, String updated) {
        return """
                {"webhookEvent":"jira:issue_updated","issue":{"key":"%s","fields":{"summary":"Work on %s",
                "status":{"name":"%s","statusCategory":{"key":"%s"}},"issuetype":{"name":"Story"},
                "priority":{"name":"High"},"assignee":null,"updated":"%s"}}}"""
                .formatted(key, key, status, category, updated);
    }

    private static String sign(String secret, String body) throws Exception {
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
}
