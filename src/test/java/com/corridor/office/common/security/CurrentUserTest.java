package com.corridor.office.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class CurrentUserTest {

    @Test
    void shouldReadIdsAndRoleFromTheToken() {
        var userId = UUID.randomUUID();
        var organizationId = UUID.randomUUID();
        var jwt = Jwt.withTokenValue("t")
                .header("alg", "RS256")
                .subject(userId.toString())
                .claim("org", organizationId.toString())
                .claim("role", "MANAGER")
                .issuedAt(Instant.now())
                .build();

        var current = CurrentUser.from(jwt);

        assertThat(current.userId()).isEqualTo(userId);
        assertThat(current.organizationId()).isEqualTo(organizationId);
        assertThat(current.role()).isEqualTo(Role.MANAGER);
    }
}
