package com.corridor.office.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RefreshTokenTest {

    private final Instant now = Instant.parse("2026-10-23T09:00:00Z");
    private final User user =
            User.register(IdentityFixtures.organization(), "nour@corridor.local", "Nour", Role.EMPLOYEE, "hash");

    @Test
    void shouldBeUsableWhenFresh() {
        var token = RefreshToken.issue(user, UUID.randomUUID(), new byte[] {1}, now.plus(Duration.ofDays(7)));

        assertThat(token.isUsable(now)).isTrue();
    }

    @Test
    void shouldMakeOldTokenUnusableAfterRotation() {
        var first = RefreshToken.issue(user, UUID.randomUUID(), new byte[] {1}, now.plus(Duration.ofDays(7)));

        var second = first.rotateTo(new byte[] {2}, now, now.plus(Duration.ofDays(7)));

        assertThat(first.isUsable(now)).isFalse();
        assertThat(second.isUsable(now)).isTrue();
        assertThat(second.familyId()).isEqualTo(first.familyId());
        assertThat(second.user()).isSameAs(user);
    }

    @Test
    void shouldNotBeUsableAfterExpiry() {
        var token = RefreshToken.issue(user, UUID.randomUUID(), new byte[] {1}, now.plusSeconds(1));

        assertThat(token.isUsable(now.plusSeconds(1))).isFalse();
    }

    @Test
    void shouldNotBeUsableAfterRevocation() {
        var token = RefreshToken.issue(user, UUID.randomUUID(), new byte[] {1}, now.plus(Duration.ofDays(7)));

        token.revoke(now);

        assertThat(token.isUsable(now)).isFalse();
    }
}
