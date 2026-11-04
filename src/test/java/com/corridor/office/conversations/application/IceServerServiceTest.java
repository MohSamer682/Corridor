package com.corridor.office.conversations.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class IceServerServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-04T10:00:00Z");
    private final UUID nour = UUID.randomUUID();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void shouldOfferOnlyStunWithoutATurnSecret() {
        var media = new MediaProperties(List.of("stun:stun.example:3478"), List.of("turn:turn.example:3478"), "");

        var servers = new IceServerService(media, clock).forPerson(nour);

        assertThat(servers.iceServers()).singleElement().satisfies(server -> {
            assertThat(server.urls()).containsExactly("stun:stun.example:3478");
            assertThat(server.username()).isNull();
        });
    }

    @Test
    void shouldSignTurnCredentialsTheWayCoturnChecksThem() throws Exception {
        var media = new MediaProperties(List.of("stun:s"), List.of("turn:turn.example:3478"), "local-secret");

        var servers = new IceServerService(media, clock).forPerson(nour);

        var turn = servers.iceServers().getLast();
        var expiry = NOW.plusSeconds(600).getEpochSecond();
        assertThat(turn.username()).isEqualTo(expiry + ":" + nour);
        var mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec("local-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        var expected =
                Base64.getEncoder().encodeToString(mac.doFinal(turn.username().getBytes(StandardCharsets.UTF_8)));
        assertThat(turn.credential()).isEqualTo(expected);
        assertThat(servers.expiresAt()).isEqualTo(NOW.plusSeconds(600));
    }
}
