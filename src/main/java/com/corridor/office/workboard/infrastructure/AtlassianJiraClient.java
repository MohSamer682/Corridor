package com.corridor.office.workboard.infrastructure;

import com.corridor.office.workboard.application.JiraClient;
import com.corridor.office.workboard.application.JiraIssues;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.StreamSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

/** Jira Cloud's REST API v3 with an API token. Reads retry twice with jitter; anything 4xx is not retried. */
@Component
class AtlassianJiraClient implements JiraClient {

    private static final int ATTEMPTS = 3;
    private static final String FIELDS = "summary,status,issuetype,priority,assignee,updated,customfield_10016";
    private static final String JQL =
            "project = \"%s\" AND (statusCategory != Done OR updated >= -14d) ORDER BY updated DESC";

    private final RestClient http;

    @Autowired
    AtlassianJiraClient() {
        this(RestClient.builder().requestFactory(timeouts()).build());
    }

    AtlassianJiraClient(RestClient http) {
        this.http = http;
    }

    @Override
    public List<JiraIssue> issues(Credentials credentials, String projectKey) {
        // shortcut: one page of 100 issues, most recently updated first; page through nextPageToken if boards grow.
        var uri = UriComponentsBuilder.fromUriString(credentials.siteUrl())
                .path("/rest/api/3/search/jql")
                .queryParam("jql", JQL.formatted(projectKey))
                .queryParam("fields", FIELDS)
                .queryParam("maxResults", 100)
                .encode()
                .build()
                .toUri();
        return StreamSupport.stream(read(uri, credentials).path("issues").spliterator(), false)
                .map(JiraIssues::read)
                .toList();
    }

    private JsonNode read(URI uri, Credentials credentials) {
        RestClientException last = null;
        for (var attempt = 1; attempt <= ATTEMPTS; attempt++) {
            try {
                return http.get()
                        .uri(uri)
                        .header("Authorization", basic(credentials))
                        .header("Accept", "application/json")
                        .retrieve()
                        .body(JsonNode.class);
            } catch (RestClientResponseException e) {
                if (!retryable(e.getStatusCode().value())) {
                    throw new JiraUnreachableException(
                            "Jira answered " + e.getStatusCode().value(), e);
                }
                last = e;
            } catch (RestClientException e) {
                last = e;
            }
            pause(attempt);
        }
        throw new JiraUnreachableException("Jira didn't answer after " + ATTEMPTS + " tries", last);
    }

    private static boolean retryable(int status) {
        return status == 429 || status >= 500;
    }

    private static void pause(int attempt) {
        if (attempt == ATTEMPTS) {
            return;
        }
        try {
            Thread.sleep(150L * attempt + ThreadLocalRandom.current().nextLong(100));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String basic(Credentials credentials) {
        var pair = credentials.email() + ":" + credentials.apiToken();
        return "Basic " + Base64.getEncoder().encodeToString(pair.getBytes(StandardCharsets.UTF_8));
    }

    private static SimpleClientHttpRequestFactory timeouts() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return factory;
    }
}
