package com.corridor.office.identity.api;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.corridor.office.config.SecurityConfig;
import com.corridor.office.identity.application.AccessToken;
import com.corridor.office.identity.application.AuthService;
import com.corridor.office.identity.application.InvalidCredentialsException;
import com.corridor.office.identity.application.RefreshTokenValue;
import com.corridor.office.identity.application.SessionEndedException;
import com.corridor.office.identity.application.SignedInSession;
import com.corridor.office.identity.application.TooManyAttemptsException;
import com.corridor.office.identity.domain.IdentityFixtures;
import com.corridor.office.identity.domain.Role;
import com.corridor.office.identity.domain.User;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, RefreshCookies.class, AuthControllerTest.FixedClock.class})
@ActiveProfiles("test")
class AuthControllerTest {

    static final Instant NOW = Instant.parse("2026-10-23T09:00:00Z");
    static final String LOGIN = "{\"email\":\"nour@corridor.local\",\"password\":\"Correct-horse-1\"}";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AuthService auth;

    @MockitoBean
    JwtDecoder jwtDecoder;

    private final SignedInSession session = new SignedInSession(
            new AccessToken("access-jwt", NOW.plusSeconds(600)),
            new RefreshTokenValue("refresh-raw", NOW.plus(Duration.ofDays(7))),
            nour());

    @Test
    void shouldReturnTokenAndSetRefreshCookieOnSignIn() throws Exception {
        given(auth.signIn("nour@corridor.local", "Correct-horse-1", "127.0.0.1"))
                .willReturn(session);

        mvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON).content(LOGIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-jwt"))
                .andExpect(jsonPath("$.expiresIn").value(600))
                .andExpect(jsonPath("$.user.email").value("nour@corridor.local"))
                .andExpect(jsonPath("$.user.role").value("EMPLOYEE"))
                .andExpect(cookie().value("corridor_refresh", "refresh-raw"))
                .andExpect(cookie().httpOnly("corridor_refresh", true))
                .andExpect(cookie().path("corridor_refresh", "/api/v1/auth"))
                .andExpect(cookie().maxAge(
                                "corridor_refresh", (int) Duration.ofDays(7).toSeconds()))
                .andExpect(header().string("Set-Cookie", containsString("SameSite=Strict")));
    }

    @Test
    void shouldRejectPasswordLongerThan128Characters() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("{\"email\":\"nour@corridor.local\",\"password\":\"" + "x".repeat(129) + "\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("password"))
                .andExpect(jsonPath("$.errors[0].message").value("Use 8 to 128 characters."));
        verifyNoInteractions(auth);
    }

    @Test
    void shouldRejectAMalformedEmail() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("{\"email\":\"nour\",\"password\":\"Correct-horse-1\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].message").value("Enter your work email."));
        verifyNoInteractions(auth);
    }

    @Test
    void shouldAnswer401WithCopyWhenCredentialsAreWrong() throws Exception {
        given(auth.signIn(any(), any(), any())).willThrow(new InvalidCredentialsException());

        mvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON).content(LOGIN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.detail").value("Email or password is incorrect."));
    }

    @Test
    void shouldSendRetryAfterWhenRateLimited() throws Exception {
        given(auth.signIn(any(), any(), any())).willThrow(new TooManyAttemptsException(Duration.ofSeconds(42)));

        mvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON).content(LOGIN))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "42"))
                .andExpect(jsonPath("$.detail").value("Too many attempts. Try again in a minute."));
    }

    @Test
    void shouldRotateWithTheRefreshCookie() throws Exception {
        given(auth.refresh("refresh-old")).willReturn(session);

        mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("corridor_refresh", "refresh-old")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-jwt"))
                .andExpect(cookie().value("corridor_refresh", "refresh-raw"));
    }

    @Test
    void shouldReturnSessionEndedWhenRefreshCookieIsMissing() throws Exception {
        mvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_ENDED"));
        verifyNoInteractions(auth);
    }

    @Test
    void shouldClearTheCookieWhenRefreshFails() throws Exception {
        given(auth.refresh("refresh-old")).willThrow(new SessionEndedException());

        mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("corridor_refresh", "refresh-old")))
                .andExpect(status().isUnauthorized())
                .andExpect(cookie().maxAge("corridor_refresh", 0));
    }

    @Test
    void shouldClearCookieAndReturnJsonOnSignOut() throws Exception {
        mvc.perform(post("/api/v1/auth/logout").cookie(new Cookie("corridor_refresh", "abc")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SIGNED_OUT"))
                .andExpect(cookie().maxAge("corridor_refresh", 0));
        then(auth).should().signOut("abc");
    }

    private static User nour() {
        var user = User.register(IdentityFixtures.organization(), "nour@corridor.local", "Nour", Role.EMPLOYEE, "x");
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClock {

        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
