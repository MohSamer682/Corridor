package com.corridor.office.identity.application;

import static com.corridor.office.support.ConcurrentRuns.runTwiceConcurrently;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.corridor.office.TestcontainersConfiguration;
import com.corridor.office.identity.domain.IdentityFixtures;
import com.corridor.office.identity.domain.Role;
import com.corridor.office.identity.domain.User;
import com.corridor.office.identity.infrastructure.OrganizationRepository;
import com.corridor.office.identity.infrastructure.UserRepository;
import com.corridor.office.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@Import({TestcontainersConfiguration.class, AuthServiceIntegrationTest.ClockConfig.class})
@ActiveProfiles("test")
class AuthServiceIntegrationTest {

    private static final String PASSWORD = "Correct-horse-1";
    private static final String IP = "10.0.0.1";

    @Autowired
    AuthService auth;

    @Autowired
    OrganizationRepository organizations;

    @Autowired
    UserRepository users;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    MutableClock clock;

    @BeforeEach
    void createUser() {
        clock.advance(Duration.ofSeconds(61));
        var organization = organizations.save(IdentityFixtures.organization());
        users.save(User.register(
                organization, "nour@corridor.local", "Nour", Role.EMPLOYEE, passwordEncoder.encode(PASSWORD)));
    }

    @AfterEach
    void clearTables() {
        jdbc.sql("truncate refresh_tokens, users, organizations cascade").update();
    }

    @Test
    void shouldSignInAndStoreOneRefreshToken() {
        var session = auth.signIn("nour@corridor.local", PASSWORD, IP);

        assertThat(session.accessToken().value()).isNotBlank();
        assertThat(session.user().email()).isEqualTo("nour@corridor.local");
        assertThat(count("select count(*) from refresh_tokens")).isEqualTo(1);
    }

    @Test
    void shouldSignInWhenEmailHasCapitalsAndSpaces() {
        assertThatCode(() -> auth.signIn("  Nour@Corridor.LOCAL ", PASSWORD, IP))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldReturnSameErrorForUnknownEmailAndWrongPassword() {
        var unknown = catchThrowable(() -> auth.signIn("ghost@corridor.local", PASSWORD, IP));
        var wrong = catchThrowable(() -> auth.signIn("nour@corridor.local", "Wrong-horse-1", IP));

        assertThat(unknown).isInstanceOf(InvalidCredentialsException.class);
        assertThat(wrong).isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void shouldRefuseSuspendedUsers() {
        suspend("nour@corridor.local");

        assertThatThrownBy(() -> auth.signIn("nour@corridor.local", PASSWORD, IP))
                .isInstanceOf(AccountSuspendedException.class);
    }

    @Test
    void shouldRotateRefreshTokenAndKeepTheFamily() {
        var first = auth.signIn("nour@corridor.local", PASSWORD, IP);

        var second = auth.refresh(first.refreshToken().raw());

        assertThat(second.refreshToken().raw())
                .isNotEqualTo(first.refreshToken().raw());
        assertThat(count("select count(distinct family_id) from refresh_tokens"))
                .isEqualTo(1);
        assertThat(
                        count(
                                "select count(*) from refresh_tokens where revoked_at is not null and replaced_by_id is not null"))
                .isEqualTo(1);
    }

    @Test
    void shouldRevokeTheFamilyWhenAnOldRefreshTokenIsReused() {
        var first = auth.signIn("nour@corridor.local", PASSWORD, IP);
        var second = auth.refresh(first.refreshToken().raw());

        assertThatThrownBy(() -> auth.refresh(first.refreshToken().raw())).isInstanceOf(SessionEndedException.class);
        assertThatThrownBy(() -> auth.refresh(second.refreshToken().raw())).isInstanceOf(SessionEndedException.class);
        assertThat(count("select count(*) from refresh_tokens where revoked_at is null"))
                .isZero();
    }

    @Test
    void shouldEndTheSessionWhenTheRefreshTokenExpired() {
        var session = auth.signIn("nour@corridor.local", PASSWORD, IP);

        clock.advance(Duration.ofDays(8));

        assertThatThrownBy(() -> auth.refresh(session.refreshToken().raw())).isInstanceOf(SessionEndedException.class);
    }

    @Test
    void shouldEndTheSessionForAnUnknownOrMalformedToken() {
        assertThatThrownBy(() -> auth.refresh("not-a-token!")).isInstanceOf(SessionEndedException.class);
        assertThatThrownBy(() -> auth.refresh("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"))
                .isInstanceOf(SessionEndedException.class);
    }

    @Test
    void shouldEndTheSessionWhenTheUserWasSuspended() {
        var session = auth.signIn("nour@corridor.local", PASSWORD, IP);
        suspend("nour@corridor.local");

        assertThatThrownBy(() -> auth.refresh(session.refreshToken().raw())).isInstanceOf(SessionEndedException.class);
    }

    @Test
    void shouldLetOnlyOneOfTwoConcurrentRefreshesWin() throws Exception {
        var first = auth.signIn("nour@corridor.local", PASSWORD, IP);

        var results =
                runTwiceConcurrently(() -> auth.refresh(first.refreshToken().raw()));

        assertThat(results.successes()).isEqualTo(1);
        assertThat(results.failures()).allMatch(SessionEndedException.class::isInstance);
    }

    @Test
    void shouldRevokeTheFamilyOnSignOut() {
        var session = auth.signIn("nour@corridor.local", PASSWORD, IP);

        auth.signOut(session.refreshToken().raw());

        assertThatThrownBy(() -> auth.refresh(session.refreshToken().raw())).isInstanceOf(SessionEndedException.class);
    }

    @Test
    void shouldIgnoreSignOutWithAnUnknownToken() {
        assertThatCode(() -> auth.signOut("not-a-token!")).doesNotThrowAnyException();
    }

    private long count(String sql) {
        return jdbc.sql(sql).query(Long.class).single();
    }

    private void suspend(String email) {
        jdbc.sql("update users set status = 'SUSPENDED' where email = :email")
                .param("email", email)
                .update();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ClockConfig {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(Instant.now());
        }
    }
}
