package com.corridor.office.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.corridor.office.TestcontainersConfiguration;
import com.corridor.office.common.domain.ConflictException;
import com.corridor.office.common.domain.NotFoundException;
import com.corridor.office.common.security.CurrentUser;
import com.corridor.office.common.security.Role;
import com.corridor.office.identity.domain.IdentityFixtures;
import com.corridor.office.identity.domain.Organization;
import com.corridor.office.identity.domain.Team;
import com.corridor.office.identity.domain.User;
import com.corridor.office.identity.domain.UserStatus;
import com.corridor.office.identity.domain.events.UserSuspended;
import com.corridor.office.identity.infrastructure.OrganizationRepository;
import com.corridor.office.identity.infrastructure.TeamRepository;
import com.corridor.office.identity.infrastructure.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@RecordApplicationEvents
@WithMockUser(roles = "ADMIN")
class UserServiceIntegrationTest {

    private static final String PASSWORD = "Welcome-pass-1";

    @Autowired
    UserService userService;

    @Autowired
    UserAccessService access;

    @Autowired
    AuthService auth;

    @Autowired
    OrganizationRepository organizations;

    @Autowired
    UserRepository users;

    @Autowired
    TeamRepository teams;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    @Autowired
    ApplicationEvents events;

    private Organization organization;
    private User admin;
    private CurrentUser actor;

    @BeforeEach
    void createAdmin() {
        organization = organizations.save(IdentityFixtures.organization());
        admin = users.save(User.register(
                organization, "admin@corridor.local", "Admin", Role.ADMIN, passwordEncoder.encode(PASSWORD)));
        actor = new CurrentUser(admin.id(), organization.id(), Role.ADMIN);
    }

    @AfterEach
    void clearTables() {
        jdbc.sql("truncate refresh_tokens, users, teams, organizations cascade").update();
    }

    @Test
    void shouldCreateAUserWhoCanSignIn() {
        var created = userService.create(actor, newUser("nour@corridor.local", Role.EMPLOYEE, null));

        assertThat(created.status()).isEqualTo(UserStatus.ACTIVE);
        assertThatCode(() -> auth.signIn("nour@corridor.local", PASSWORD, "10.1.0.1"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectADuplicateEmail() {
        assertThatThrownBy(() -> userService.create(actor, newUser("Admin@Corridor.local", Role.EMPLOYEE, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("users.duplicateEmail");
    }

    @Test
    void shouldRefuseToSuspendYourself() {
        assertThatThrownBy(() -> access.suspend(actor, admin.id())).hasMessage("users.self.suspend");
    }

    @Test
    void shouldRefuseToRemoveYourself() {
        assertThatThrownBy(() -> access.remove(actor, admin.id())).hasMessage("users.self.remove");
    }

    @Test
    void shouldRefuseToChangeYourOwnRole() {
        assertThatThrownBy(() -> userService.update(actor, admin.id(), admin.version(), changes(Role.EMPLOYEE)))
                .hasMessage("users.self.role");
    }

    @Test
    void shouldRefuseToDemoteTheLastActiveAdmin() {
        var suspendedAdmin = otherAdminWhoIsSuspended();

        assertThatThrownBy(
                        () -> userService.update(suspendedAdmin, admin.id(), admin.version(), changes(Role.EMPLOYEE)))
                .hasMessage("users.lastAdmin");
    }

    @Test
    void shouldRefuseToRemoveTheLastActiveAdmin() {
        var suspendedAdmin = otherAdminWhoIsSuspended();

        assertThatThrownBy(() -> access.remove(suspendedAdmin, admin.id())).hasMessage("users.lastAdmin");
    }

    @Test
    void shouldRejectAStaleVersion() {
        var nour = userService.create(actor, newUser("nour@corridor.local", Role.EMPLOYEE, null));
        userService.update(actor, nour.id(), nour.version(), changes(Role.MANAGER));

        assertThatThrownBy(() -> userService.update(actor, nour.id(), nour.version(), changes(Role.EMPLOYEE)))
                .hasMessage("users.stale");
    }

    @Test
    void shouldRevokeRefreshTokensWhenSuspending() {
        var nour = userService.create(actor, newUser("nour@corridor.local", Role.EMPLOYEE, null));
        var session = auth.signIn("nour@corridor.local", PASSWORD, "10.1.0.2");

        access.suspend(actor, nour.id());

        assertThatThrownBy(() -> auth.refresh(session.refreshToken().raw())).isInstanceOf(SessionEndedException.class);
        assertThat(events.stream(UserSuspended.class)).hasSize(1);
    }

    @Test
    void shouldNotFindUsersFromAnotherOrganization() {
        var elsewhere = organizations.save(IdentityFixtures.organization("elsewhere"));
        var stranger = users.save(User.register(elsewhere, "x@elsewhere.test", "X", Role.EMPLOYEE, "{noop}x"));

        assertThatThrownBy(() -> userService.get(actor, stranger.id())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> access.suspend(actor, stranger.id())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void shouldListWithTeamNameInTwoQueries() {
        var design = teams.save(Team.create(organization, "Design", "#23395D"));
        for (int i = 0; i < 25; i++) {
            userService.create(actor, newUser("p" + i + "@corridor.local", Role.EMPLOYEE, design.id()));
        }
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        var page = userService.list(actor, UserStatus.ACTIVE, design.id(), "P1", PageRequest.of(0, 5));

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
        assertThat(page.items()).hasSize(5);
        assertThat(page.totalItems()).isEqualTo(11);
        assertThat(page.items())
                .allSatisfy(summary -> assertThat(summary.teamName()).isEqualTo("Design"));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void shouldDenyEmployees() {
        assertThatThrownBy(() -> userService.list(actor, null, null, null, PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);
    }

    private CurrentUser otherAdminWhoIsSuspended() {
        var other = userService.create(actor, newUser("second@corridor.local", Role.ADMIN, null));
        access.suspend(actor, other.id());
        return new CurrentUser(other.id(), organization.id(), Role.ADMIN);
    }

    private static NewUser newUser(String email, Role role, UUID teamId) {
        return new NewUser(email, "Person", null, role, teamId, PASSWORD);
    }

    private static UserChanges changes(Role role) {
        return new UserChanges("Person", null, role, null);
    }
}
