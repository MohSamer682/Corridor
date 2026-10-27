package com.corridor.office.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.corridor.office.TestcontainersConfiguration;
import com.corridor.office.identity.domain.IdentityFixtures;
import com.corridor.office.identity.domain.Role;
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
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class AuthFlowIntegrationTest {

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
    void createUser() {
        var organization = organizations.save(IdentityFixtures.organization());
        users.save(User.register(
                organization, "nour@corridor.local", "Nour", Role.EMPLOYEE, passwordEncoder.encode("Correct-horse-1")));
    }

    @AfterEach
    void clearTables() {
        jdbc.sql("truncate refresh_tokens, users, organizations cascade").update();
    }

    @Test
    void shouldSignInRefreshDetectReuseAndSignOutWithRealRows() {
        var login = http.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", "nour@corridor.local", "password", "Correct-horse-1"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(Map.class)
                .returnResult();
        var firstCookie = refreshCookie(login);
        assertThat(count("select count(*) from refresh_tokens")).isEqualTo(1);

        http.get()
                .uri("/api/v1/me")
                .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + login.getResponseBody().get("accessToken"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.email")
                .isEqualTo("nour@corridor.local");

        var refreshed =
                refresh(firstCookie).expectStatus().isOk().expectBody(Map.class).returnResult();
        var secondCookie = refreshCookie(refreshed);
        assertThat(secondCookie).isNotEqualTo(firstCookie);
        assertThat(count("select count(*) from refresh_tokens")).isEqualTo(2);
        assertThat(
                        count(
                                "select count(*) from refresh_tokens where revoked_at is not null and replaced_by_id is not null"))
                .isEqualTo(1);

        refresh(firstCookie).expectStatus().isUnauthorized();
        assertThat(count("select count(*) from refresh_tokens where revoked_at is null"))
                .isZero();

        http.post()
                .uri("/api/v1/auth/logout")
                .header(HttpHeaders.COOKIE, secondCookie)
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .value(HttpHeaders.SET_COOKIE, header -> assertThat(header).contains("Max-Age=0"))
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo("SIGNED_OUT");
    }

    private RestTestClient.ResponseSpec refresh(String cookie) {
        return http.post()
                .uri("/api/v1/auth/refresh")
                .header(HttpHeaders.COOKIE, cookie)
                .exchange();
    }

    private static String refreshCookie(EntityExchangeResult<?> result) {
        var header = result.getResponseHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(header).startsWith("corridor_refresh=").contains("HttpOnly", "Path=/api/v1/auth");
        return header.substring(0, header.indexOf(';'));
    }

    private long count(String sql) {
        return jdbc.sql(sql).query(Long.class).single();
    }
}
