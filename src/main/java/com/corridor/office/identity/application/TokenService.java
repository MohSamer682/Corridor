package com.corridor.office.identity.application;

import com.corridor.office.identity.domain.User;
import com.corridor.office.identity.infrastructure.JwtProperties;
import java.time.Clock;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class TokenService {

    private final JwtEncoder encoder;
    private final JwtProperties properties;
    private final Clock clock;

    public TokenService(JwtEncoder encoder, JwtProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    public AccessToken issueAccessToken(User user) {
        var now = clock.instant();
        var expiresAt = now.plus(properties.accessTokenTtl());
        var claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(user.id().toString())
                .claim("org", user.organization().id().toString())
                .claim("role", user.role().name())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .build();
        var header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        var token = encoder.encode(JwtEncoderParameters.from(header, claims));
        return new AccessToken(token.getTokenValue(), expiresAt);
    }
}
