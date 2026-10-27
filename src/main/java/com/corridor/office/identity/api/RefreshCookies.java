package com.corridor.office.identity.api;

import com.corridor.office.identity.application.RefreshTokenValue;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties(RefreshCookieProperties.class)
class RefreshCookies {

    static final String NAME = "corridor_refresh";

    private final RefreshCookieProperties properties;
    private final Clock clock;

    RefreshCookies(RefreshCookieProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    void write(HttpServletResponse response, RefreshTokenValue token) {
        add(response, token.raw(), Duration.between(clock.instant(), token.expiresAt()));
    }

    void clear(HttpServletResponse response) {
        add(response, "", Duration.ZERO);
    }

    private void add(HttpServletResponse response, String value, Duration maxAge) {
        var cookie = ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(properties.secure())
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
