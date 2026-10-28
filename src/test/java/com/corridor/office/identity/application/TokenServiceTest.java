package com.corridor.office.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.corridor.office.identity.domain.IdentityFixtures;
import com.corridor.office.identity.domain.Role;
import com.corridor.office.identity.domain.User;
import com.corridor.office.identity.infrastructure.JwtConfig;
import com.corridor.office.identity.infrastructure.JwtProperties;
import com.corridor.office.identity.infrastructure.LocalJwtKeysConfig;
import com.nimbusds.jose.jwk.RSAKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.test.util.ReflectionTestUtils;

class TokenServiceTest {

    private final Instant fixedNow = Instant.parse("2026-10-23T09:00:00Z");
    private final Clock clock = Clock.fixed(fixedNow, ZoneOffset.UTC);
    private final JwtProperties properties = properties("corridor");
    private final JwtConfig jwtConfig = new JwtConfig();
    private final RSAKey signingKey = new LocalJwtKeysConfig().corridorSigningKey();
    private final TokenService tokenService = new TokenService(jwtConfig.jwtEncoder(signingKey), properties, clock);
    private final JwtDecoder jwtDecoder = jwtConfig.jwtDecoder(signingKey, properties, clock);
    private final User admin =
            persisted(User.register(IdentityFixtures.organization(), "a@corridor.local", "A", Role.ADMIN, "x"));

    @Test
    void shouldIssueTokenThatDecodesWithUserClaims() {
        var token = tokenService.issueAccessToken(admin);
        var jwt = jwtDecoder.decode(token.value());

        assertThat(jwt.getSubject()).isEqualTo(admin.id().toString());
        assertThat(jwt.getClaimAsString("org"))
                .isEqualTo(admin.organization().id().toString());
        assertThat(jwt.getClaimAsString("role")).isEqualTo("ADMIN");
        assertThat(token.expiresAt()).isEqualTo(fixedNow.plus(Duration.ofMinutes(10)));
    }

    @Test
    void shouldRejectTokenFromAnotherIssuer() {
        var foreign = new TokenService(jwtConfig.jwtEncoder(signingKey), properties("someone-else"), clock);

        var token = foreign.issueAccessToken(admin);

        assertThatThrownBy(() -> jwtDecoder.decode(token.value())).isInstanceOf(JwtValidationException.class);
    }

    @Test
    void shouldRejectExpiredToken() {
        var token = tokenService.issueAccessToken(admin);
        var later = Clock.fixed(fixedNow.plus(Duration.ofMinutes(15)), ZoneOffset.UTC);

        var decoderLater = jwtConfig.jwtDecoder(signingKey, properties, later);

        assertThatThrownBy(() -> decoderLater.decode(token.value())).isInstanceOf(JwtValidationException.class);
    }

    private static JwtProperties properties(String issuer) {
        return new JwtProperties(issuer, Duration.ofMinutes(10), Duration.ofDays(7), null, null);
    }

    private static User persisted(User user) {
        ReflectionTestUtils.setField(user.organization(), "id", UUID.randomUUID());
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }
}
