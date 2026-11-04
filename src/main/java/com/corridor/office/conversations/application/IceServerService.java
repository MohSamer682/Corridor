package com.corridor.office.conversations.application;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

/** ICE servers for one person, with coturn's time-limited credentials (its REST API scheme). */
@Service
@EnableConfigurationProperties(MediaProperties.class)
public class IceServerService {

    private static final Duration LIFETIME = Duration.ofMinutes(10);

    public record IceServer(List<String> urls, String username, String credential) {}

    public record IceServers(List<IceServer> iceServers, Instant expiresAt) {}

    private final MediaProperties media;
    private final Clock clock;

    IceServerService(MediaProperties media, Clock clock) {
        this.media = media;
        this.clock = clock;
    }

    public IceServers forPerson(UUID userId) {
        var expiresAt = clock.instant().plus(LIFETIME);
        var servers = new ArrayList<IceServer>();
        servers.add(new IceServer(media.stunUrls(), null, null));
        if (!media.turnSecret().isBlank() && !media.turnUrls().isEmpty()) {
            var username = expiresAt.getEpochSecond() + ":" + userId;
            servers.add(new IceServer(media.turnUrls(), username, sign(username)));
        }
        return new IceServers(List.copyOf(servers), expiresAt);
    }

    private String sign(String username) {
        try {
            var mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(media.turnSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return Base64.getEncoder().encodeToString(mac.doFinal(username.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException missing) {
            throw new IllegalStateException("HmacSHA1 is not available", missing);
        }
    }
}
