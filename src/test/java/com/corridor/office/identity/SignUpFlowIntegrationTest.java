package com.corridor.office.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.corridor.office.TestcontainersConfiguration;
import com.corridor.office.common.security.Role;
import com.corridor.office.identity.domain.IdentityFixtures;
import com.corridor.office.identity.domain.Team;
import com.corridor.office.identity.domain.User;
import com.corridor.office.identity.infrastructure.OrganizationRepository;
import com.corridor.office.identity.infrastructure.TeamRepository;
import com.corridor.office.identity.infrastructure.UserRepository;
import com.corridor.office.support.MutableClock;
import com.corridor.office.support.MutableClockConfiguration;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import({TestcontainersConfiguration.class, MutableClockConfiguration.class})
@ActiveProfiles("test")
class SignUpFlowIntegrationTest {

    private static final String PASSWORD = "Corridor-pass-1";

    @Autowired
    RestTestClient http;

    @Autowired
    OrganizationRepository organizations;

    @Autowired
    UserRepository users;

    @Autowired
    TeamRepository teams;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    MutableClock clock;

    private Team product;

    @BeforeEach
    void createStaff() {
        clock.advance(Duration.ofSeconds(61));
        var organization = organizations.save(IdentityFixtures.organization());
        product = teams.save(Team.create(organization, "Product", "#2F7D6D"));
        for (var person : Map.of(
                        "admin@corridor.local", Role.ADMIN,
                        "hr@corridor.local", Role.HR,
                        "nour@corridor.local", Role.EMPLOYEE)
                .entrySet()) {
            users.save(User.register(
                    organization, person.getKey(), "Staff", person.getValue(), passwordEncoder.encode(PASSWORD)));
        }
    }

    @AfterEach
    void clearTables() {
        jdbc.sql("truncate refresh_tokens, users, teams, organizations cascade").update();
    }

    @Test
    void shouldNotSignInWhilePending() {
        signUp("zeinab@corridor.local");

        http.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", "zeinab@corridor.local", "password", PASSWORD))
                .exchange()
                .expectStatus()
                .isForbidden()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("ACCOUNT_PENDING")
                .jsonPath("$.detail")
                .isEqualTo("Your account is waiting for approval. HR will let you know.");
    }

    @Test
    void shouldAnswerTheSameForADuplicateSignUp() {
        signUp("nour@corridor.local");

        assertThat(count("select count(*) from users")).isEqualTo(3);
        assertThat(count("select count(*) from users where status = 'PENDING'")).isZero();
    }

    @Test
    void shouldListPendingPeopleOnlyWhenAskedFor() {
        signUp("zeinab@corridor.local");
        var hr = token("hr@corridor.local");

        get("/api/v1/users", hr).jsonPath("$.totalItems").isEqualTo(3);
        get("/api/v1/users?status=PENDING", hr).jsonPath("$.items[0].email").isEqualTo("zeinab@corridor.local");
    }

    @Test
    void shouldApproveWithRoleAndTeam() {
        signUp("zeinab@corridor.local");
        var zeinab = pendingId("zeinab@corridor.local");

        http.post()
                .uri("/api/v1/users/" + zeinab + "/approval")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("hr@corridor.local"))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("role", "MANAGER", "teamId", product.id().toString()))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo("ACTIVE");

        assertThat(count("select count(*) from users where email = 'zeinab@corridor.local'"
                        + " and status = 'ACTIVE' and role = 'MANAGER' and team_id is not null"))
                .isEqualTo(1);
        token("zeinab@corridor.local");
    }

    @Test
    void shouldDeclineAndDeleteTheRequest() {
        signUp("zeinab@corridor.local");
        var zeinab = pendingId("zeinab@corridor.local");

        http.delete()
                .uri("/api/v1/users/" + zeinab + "/approval")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("hr@corridor.local"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo("DECLINED");

        assertThat(count("select count(*) from users where email = 'zeinab@corridor.local'"))
                .isZero();
    }

    @Test
    void shouldDenyApprovalToEmployees() {
        signUp("zeinab@corridor.local");

        approve(pendingId("zeinab@corridor.local"), token("nour@corridor.local"), "EMPLOYEE")
                .expectStatus()
                .isForbidden();
    }

    @Test
    void shouldNotLetHrGrantAdmin() {
        signUp("zeinab@corridor.local");

        approve(pendingId("zeinab@corridor.local"), token("hr@corridor.local"), "ADMIN")
                .expectStatus()
                .isForbidden()
                .expectBody()
                .jsonPath("$.detail")
                .isEqualTo("Only an admin can change admin access.");
    }

    private void signUp(String email) {
        http.post()
                .uri("/api/v1/auth/registrations")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("displayName", "Zeinab Ali", "email", email, "password", PASSWORD))
                .exchange()
                .expectStatus()
                .isAccepted()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo("PENDING");
    }

    private RestTestClient.ResponseSpec approve(UUID id, String token, String role) {
        return http.post()
                .uri("/api/v1/users/" + id + "/approval")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("role", role))
                .exchange();
    }

    private RestTestClient.BodyContentSpec get(String uri, String token) {
        return http.get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody();
    }

    private String token(String email) {
        return String.valueOf(http.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", PASSWORD))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody()
                .get("accessToken"));
    }

    private UUID pendingId(String email) {
        return jdbc.sql("select id from users where email = :email and status = 'PENDING'")
                .param("email", email)
                .query(UUID.class)
                .single();
    }

    private long count(String sql) {
        return jdbc.sql(sql).query(Long.class).single();
    }
}
