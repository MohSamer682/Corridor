package com.corridor.office.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.corridor.office.TestcontainersConfiguration;
import com.corridor.office.common.security.Role;
import com.corridor.office.identity.domain.IdentityFixtures;
import com.corridor.office.identity.domain.User;
import com.corridor.office.identity.infrastructure.OrganizationRepository;
import com.corridor.office.identity.infrastructure.UserRepository;
import java.util.Map;
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
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class UserManagementFlowIntegrationTest {

    @Autowired
    RestTestClient http;

    @Autowired
    OrganizationRepository organizations;

    @Autowired
    UserRepository users;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void createAdmin() {
        var organization = organizations.save(IdentityFixtures.organization());
        users.save(User.register(
                organization, "admin@corridor.local", "Admin", Role.ADMIN, passwordEncoder.encode("Admin-pass-1")));
    }

    @AfterEach
    void clearTables() {
        jdbc.sql("truncate refresh_tokens, users, teams, organizations cascade").update();
    }

    @Test
    void shouldAddSuspendReactivateAndRemoveAPersonWithRealRows() {
        var adminToken = signIn("admin@corridor.local", "Admin-pass-1").get("accessToken");

        var created = http.post()
                .uri("/api/v1/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "email", "nour@corridor.local",
                        "displayName", "Nour Hassan",
                        "role", "EMPLOYEE",
                        "password", "Welcome-pass-1"))
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
        var nourId = created.get("id");
        assertThat(count("select count(*) from users where email = 'nour@corridor.local' and status = 'ACTIVE'"))
                .isEqualTo(1);

        var nourToken = signIn("nour@corridor.local", "Welcome-pass-1").get("accessToken");
        http.get()
                .uri("/api/v1/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + nourToken)
                .exchange()
                .expectStatus()
                .isForbidden();

        http.post()
                .uri("/api/v1/users/" + nourId + "/suspension")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .exchange()
                .expectStatus()
                .isOk();
        assertThat(count("select count(*) from users where status = 'SUSPENDED'"))
                .isEqualTo(1);
        assertThat(count("select count(*) from refresh_tokens t join users u on u.id = t.user_id"
                        + " where u.email = 'nour@corridor.local' and t.revoked_at is null"))
                .isZero();

        http.delete()
                .uri("/api/v1/users/" + nourId + "/suspension")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .exchange()
                .expectStatus()
                .isOk();
        assertThat(count("select count(*) from users where status = 'ACTIVE'")).isEqualTo(2);

        http.delete()
                .uri("/api/v1/users/" + nourId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo("REMOVED");
        assertThat(count("select count(*) from users")).isEqualTo(1);
    }

    private Map<?, ?> signIn(String email, String password) {
        return http.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", password))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
    }

    private long count(String sql) {
        return jdbc.sql(sql).query(Long.class).single();
    }
}
