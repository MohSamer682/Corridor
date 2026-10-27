package com.corridor.office.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class JpaAuditingConfigTest {

    private final JpaAuditingConfig config = new JpaAuditingConfig();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldUseTheTokenSubjectAsAuditor() {
        var userId = UUID.randomUUID();
        var jwt = Jwt.withTokenValue("t")
                .header("alg", "RS256")
                .subject(userId.toString())
                .issuedAt(Instant.now())
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        assertThat(config.auditor().getCurrentAuditor()).contains(userId);
    }

    @Test
    void shouldHaveNoAuditorWhenAnonymous() {
        assertThat(config.auditor().getCurrentAuditor()).isEmpty();
    }
}
