package com.corridor.office.identity.api;

import com.corridor.office.common.api.ApiResult;
import com.corridor.office.identity.application.AuthService;
import com.corridor.office.identity.application.SessionEndedException;
import com.corridor.office.identity.application.SignedInSession;
import com.corridor.office.identity.application.UserView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Duration;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
class AuthController {

    private final AuthService auth;
    private final RefreshCookies cookies;
    private final Clock clock;

    AuthController(AuthService auth, RefreshCookies cookies, Clock clock) {
        this.auth = auth;
        this.cookies = cookies;
        this.clock = clock;
    }

    @PostMapping("/login")
    @Operation(summary = "Sign in with email and password")
    @ApiResponse(responseCode = "200", description = "Signed in; the refresh token is set as an HttpOnly cookie")
    @ApiResponse(responseCode = "401", description = "Email or password is incorrect")
    @ApiResponse(responseCode = "403", description = "The account is suspended")
    @ApiResponse(responseCode = "422", description = "The email or password is not valid")
    @ApiResponse(responseCode = "429", description = "Too many attempts; see Retry-After")
    TokenResponse login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest http, HttpServletResponse response) {
        return respond(auth.signIn(request.email(), request.password(), http.getRemoteAddr()), response);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Swap the refresh cookie for a new access token and cookie")
    @ApiResponse(responseCode = "200", description = "New access token; the refresh cookie is rotated")
    @ApiResponse(responseCode = "401", description = "The session has ended")
    TokenResponse refresh(
            @CookieValue(name = RefreshCookies.NAME, required = false) String refreshToken,
            HttpServletResponse response) {
        if (refreshToken == null) {
            throw new SessionEndedException();
        }
        try {
            return respond(auth.refresh(refreshToken), response);
        } catch (SessionEndedException ended) {
            cookies.clear(response);
            throw ended;
        }
    }

    @PostMapping("/logout")
    @Operation(summary = "End the session behind the refresh cookie")
    @ApiResponse(responseCode = "200", description = "Signed out; the refresh cookie is cleared")
    ApiResult logout(
            @CookieValue(name = RefreshCookies.NAME, required = false) String refreshToken,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletResponse response) {
        if (refreshToken != null) {
            auth.signOut(refreshToken);
        }
        cookies.clear(response);
        return new ApiResult(jwt == null ? null : jwt.getSubject(), "SIGNED_OUT");
    }

    private TokenResponse respond(SignedInSession session, HttpServletResponse response) {
        cookies.write(response, session.refreshToken());
        var expiresIn = Duration.between(clock.instant(), session.accessToken().expiresAt());
        return new TokenResponse(session.accessToken().value(), expiresIn.toSeconds(), UserView.of(session.user()));
    }
}
