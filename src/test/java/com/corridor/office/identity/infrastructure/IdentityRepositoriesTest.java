package com.corridor.office.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.corridor.office.TestcontainersConfiguration;
import com.corridor.office.common.security.Role;
import com.corridor.office.config.JpaAuditingConfig;
import com.corridor.office.identity.domain.IdentityFixtures;
import com.corridor.office.identity.domain.Organization;
import com.corridor.office.identity.domain.RefreshToken;
import com.corridor.office.identity.domain.Team;
import com.corridor.office.identity.domain.User;
import jakarta.persistence.EntityManager;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class IdentityRepositoriesTest {

    private final Instant now = Instant.parse("2026-10-23T09:00:00Z");
    private final Instant later = now.plus(Duration.ofDays(7));

    @Autowired
    OrganizationRepository organizations;

    @Autowired
    UserRepository users;

    @Autowired
    RefreshTokenRepository tokens;

    @Autowired
    EntityManager entityManager;

    private Organization organization;

    @BeforeEach
    void saveOrganization() {
        organization = organizations.saveAndFlush(IdentityFixtures.organization());
    }

    @Autowired
    TeamRepository teams;

    @Test
    void shouldRejectASecondTeamWithTheSameNameIgnoringCase() {
        teams.saveAndFlush(Team.create(organization, "Design", "#23395D"));

        assertThatThrownBy(() -> teams.saveAndFlush(Team.create(organization, "design", "#2F7D6D")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRefuseToDeleteATeamThatHasMembers() {
        var team = teams.saveAndFlush(Team.create(organization, "Design", "#23395D"));
        var user = User.register(organization, "t@corridor.local", "T", Role.EMPLOYEE, "{noop}x");
        user.moveToTeam(team);
        users.saveAndFlush(user);
        entityManager.clear();

        assertThatThrownBy(() -> {
                    teams.deleteById(team.id());
                    teams.flush();
                })
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldReadBackOrganizationWeekendDays() {
        entityManager.clear();

        var found = organizations.findById(organization.id()).orElseThrow();

        assertThat(found.weekendDays()).containsExactlyInAnyOrder(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY);
    }

    @Test
    void shouldFindUserByNormalizedEmailWithItsOrganization() {
        var saved = users.saveAndFlush(
                User.register(organization, "Nour@Corridor.local", "Nour", Role.EMPLOYEE, "{noop}x"));
        entityManager.clear();

        var found = users.findByEmail("nour@corridor.local").orElseThrow();

        assertThat(found.id()).isEqualTo(saved.id());
        assertThat(Hibernate.isInitialized(found.organization())).isTrue();
    }

    @Test
    void shouldRejectASecondUserWithTheSameEmail() {
        users.saveAndFlush(User.register(organization, "dup@corridor.local", "A", Role.EMPLOYEE, "{noop}x"));

        assertThatThrownBy(() -> users.saveAndFlush(
                        User.register(organization, "dup@corridor.local", "B", Role.EMPLOYEE, "{noop}x")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRevokeEveryTokenInAFamilyWithOneStatement() {
        var user = users.saveAndFlush(User.register(organization, "a@corridor.local", "A", Role.EMPLOYEE, "{noop}x"));
        var family = UUID.randomUUID();
        tokens.saveAll(List.of(
                RefreshToken.issue(user, family, new byte[] {1}, later),
                RefreshToken.issue(user, family, new byte[] {2}, later)));
        tokens.flush();

        assertThat(tokens.revokeFamily(family, now)).isEqualTo(2);
    }

    @Test
    void shouldStoreTheReplacementWhenRotating() {
        var user = users.saveAndFlush(User.register(organization, "b@corridor.local", "B", Role.EMPLOYEE, "{noop}x"));
        var first = tokens.saveAndFlush(RefreshToken.issue(user, UUID.randomUUID(), new byte[] {3}, later));
        first.rotateTo(new byte[] {4}, now, later);
        tokens.flush();
        entityManager.clear();

        var rotated = tokens.findForRotationByTokenHash(new byte[] {3}).orElseThrow();
        var replacement = tokens.findForRotationByTokenHash(new byte[] {4}).orElseThrow();

        assertThat(rotated.isUsable(now)).isFalse();
        assertThat(replacement.isUsable(now)).isTrue();
        assertThat(Hibernate.isInitialized(replacement.user())).isTrue();
    }
}
