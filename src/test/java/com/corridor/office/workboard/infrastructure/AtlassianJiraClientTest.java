package com.corridor.office.workboard.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.corridor.office.workboard.application.JiraClient.Credentials;
import com.corridor.office.workboard.application.JiraClient.JiraUnreachableException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AtlassianJiraClientTest {

    private static final String SEARCH = "https://corridor.atlassian.net/rest/api/3/search/jql";
    private static final Credentials CORRIDOR =
            new Credentials("https://corridor.atlassian.net", "admin@corridor.local", "secret-token");

    // A trimmed copy of what Jira Cloud's /rest/api/3/search/jql returns.
    private static final String ISSUES =
            """
            {"issues":[
              {"key":"COR-1","fields":{"summary":"Punch clock shows a live timer","updated":"2026-11-05T09:12:00.000+0200",
                "status":{"name":"In Progress","statusCategory":{"key":"indeterminate"}},
                "issuetype":{"name":"Story"},"priority":{"name":"High"},
                "assignee":{"displayName":"Nour Hassan"},"customfield_10016":5}},
              {"key":"COR-2","fields":{"summary":"Avatars sit on sofas","updated":"2026-11-04T17:00:00.000+0200",
                "status":{"name":"Code Review","statusCategory":{"key":"indeterminate"}},
                "issuetype":{"name":"Task"},"priority":{"name":"Medium"},"assignee":null}},
              {"key":"COR-3","fields":{"summary":"Town hall banner","updated":"2026-11-03T10:00:00.000+0200",
                "status":{"name":"Done","statusCategory":{"key":"done"}},
                "issuetype":{"name":"Bug"},"priority":{"name":"Low"},"assignee":null}},
              {"key":"COR-4","fields":{"summary":"Command center","updated":"2026-11-02T10:00:00.000+0200",
                "status":{"name":"Backlog","statusCategory":{"key":"new"}},
                "issuetype":{"name":"Story"},"priority":null,"assignee":null}}
            ],"isLast":true}""";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer jira =
            MockRestServiceServer.bindTo(builder).build();

    @Test
    void shouldSortIssuesIntoTheFourBoardColumns() {
        var auth = Base64.getEncoder()
                .encodeToString("admin@corridor.local:secret-token".getBytes(StandardCharsets.UTF_8));
        jira.expect(requestTo(startsWith(SEARCH)))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Basic " + auth))
                .andRespond(withSuccess(ISSUES, MediaType.APPLICATION_JSON));

        var issues = new AtlassianJiraClient(builder.build()).issues(CORRIDOR, "COR");

        assertThat(issues)
                .extracting(i -> i.key() + " " + i.column())
                .containsExactly("COR-1 In Progress", "COR-2 In Review", "COR-3 Done", "COR-4 To Do");
        assertThat(issues.getFirst().storyPoints()).isEqualTo(5.0);
        assertThat(issues.getFirst().assignee()).isEqualTo("Nour Hassan");
        assertThat(issues.getFirst().updated()).hasToString("2026-11-05T07:12:00Z");
        assertThat(issues.get(3).priority()).isNull();
        jira.verify();
    }

    @Test
    void shouldRetryAFailingReadTwiceThenGiveUp() {
        jira.expect(ExpectedCount.times(3), requestTo(startsWith(SEARCH))).andRespond(withServerError());

        assertThatThrownBy(() -> new AtlassianJiraClient(builder.build()).issues(CORRIDOR, "COR"))
                .isInstanceOf(JiraUnreachableException.class);
        jira.verify();
    }

    @Test
    void shouldNotRetryWrongCredentials() {
        jira.expect(ExpectedCount.once(), requestTo(startsWith(SEARCH)))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> new AtlassianJiraClient(builder.build()).issues(CORRIDOR, "COR"))
                .isInstanceOf(JiraUnreachableException.class)
                .hasMessageContaining("401");
        jira.verify();
    }
}
