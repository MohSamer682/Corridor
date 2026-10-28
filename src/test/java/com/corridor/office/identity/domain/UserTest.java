package com.corridor.office.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.corridor.office.common.security.Role;
import com.corridor.office.config.PasswordConfig;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class UserTest {

    private final Organization organization = IdentityFixtures.organization();
    private final PasswordEncoder encoder = new PasswordConfig().passwordEncoder();

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
    void shouldMatchOnlyTheRightPassword() {
        var user = User.register(organization, "a@b.c", "A", Role.EMPLOYEE, encoder.encode("Correct-horse-1"));

        assertThat(user.passwordMatches(encoder, "Correct-horse-1")).isTrue();
        assertThat(user.passwordMatches(encoder, "Wrong-horse-1")).isFalse();
    }

    @Test
    void shouldNeverMatchWhenUserHasNoPassword() {
        var user = User.register(organization, "a@b.c", "A", Role.EMPLOYEE, null);

        assertThat(user.passwordMatches(encoder, "anything")).isFalse();
    }

    @Test
    void shouldMoveUserToTeamAndOutAgain() {
        var user = User.register(organization, "a@b.c", "A", Role.EMPLOYEE, "hash");
        var team = Team.create(organization, "Design", "#23395D");

        user.moveToTeam(team);
        assertThat(user.team()).isSameAs(team);

        user.moveToTeam(null);
        assertThat(user.team()).isNull();
    }

    @Test
    void shouldChangeRoleNameAndJobTitle() {
        var user = User.register(organization, "a@b.c", "A", Role.EMPLOYEE, "hash");

        user.changeRole(Role.MANAGER);
        user.rename("  Salma Ibrahim ");
        user.changeJobTitle("Head of product");

        assertThat(user.role()).isEqualTo(Role.MANAGER);
        assertThat(user.displayName()).isEqualTo("Salma Ibrahim");
        assertThat(user.jobTitle()).isEqualTo("Head of product");
    }

    @Test
    void shouldBeActiveAgainAfterReactivation() {
        var user = User.register(organization, "a@b.c", "A", Role.EMPLOYEE, "hash");
        user.suspend();

        user.reactivate();

        assertThat(user.isActive()).isTrue();
    }
}
