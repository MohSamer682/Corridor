package com.corridor.office.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UserTest {

    private final Organization organization = IdentityFixtures.organization();

    @Test
    void shouldNormalizeEmailWhenRegistering() {
        var user = User.register(organization, "  Admin@Corridor.Local ", "Admin", Role.ADMIN, "hash");

        assertThat(user.email()).isEqualTo("admin@corridor.local");
    }

    @Test
    void shouldBeActiveAfterRegistering() {
        var user = User.register(organization, "a@b.c", "A", Role.EMPLOYEE, "hash");

        assertThat(user.isActive()).isTrue();
        assertThat(user.status()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void shouldNotBeActiveAfterSuspension() {
        var user = User.register(organization, "a@b.c", "A", Role.EMPLOYEE, "hash");

        user.suspend();

        assertThat(user.isActive()).isFalse();
    }

    @Test
    void shouldBeActiveAgainAfterReactivation() {
        var user = User.register(organization, "a@b.c", "A", Role.EMPLOYEE, "hash");
        user.suspend();

        user.reactivate();

        assertThat(user.isActive()).isTrue();
    }
}
