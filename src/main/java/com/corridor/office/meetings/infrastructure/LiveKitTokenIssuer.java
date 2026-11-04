package com.corridor.office.meetings.infrastructure;

import com.corridor.office.meetings.MediaTokens;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * LiveKit access tokens are HS256 JWTs: the API key as issuer, the person as subject, and a video grant naming the
 * room. Signed with Nimbus, already on the classpath, so no LiveKit SDK types reach the domain.
 */
@Component
@EnableConfigurationProperties(LiveKitProperties.class)
class LiveKitTokenIssuer implements MediaTokens {

    private final LiveKitProperties livekit;
    private final Clock clock;

    LiveKitTokenIssuer(LiveKitProperties livekit, Clock clock) {
        this.livekit = livekit;
        this.clock = clock;
    }

    @Override
    public Join issue(String room, UUID userId, String displayName, java.time.Instant expiresAt) {
        var now = clock.instant();
        var claims = new JWTClaimsSet.Builder()
                .issuer(livekit.apiKey())
                .subject(userId.toString())
                .notBeforeTime(Date.from(now))
                .expirationTime(Date.from(expiresAt))
                .claim("name", displayName)
                .claim("video", Map.of("room", room, "roomJoin", true, "canPublish", true, "canSubscribe", true))
                .build();
        try {
            var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(livekit.apiSecret().getBytes(StandardCharsets.UTF_8)));
            return new Join(livekit.url(), jwt.serialize());
        } catch (JOSEException badSecret) {
            throw new IllegalStateException("app.livekit.api-secret must be set and at least 32 bytes", badSecret);
        }
    }
}
