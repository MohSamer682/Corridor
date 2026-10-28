package com.corridor.office.common.security;

import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

public record CurrentUser(UUID userId, UUID organizationId, Role role) {

    public static CurrentUser from(Jwt jwt) {
        return new CurrentUser(
                UUID.fromString(jwt.getSubject()),
                UUID.fromString(jwt.getClaimAsString("org")),
                Role.valueOf(jwt.getClaimAsString("role")));
    }
}
