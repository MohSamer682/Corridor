package com.corridor.office.audit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.corridor.office.TestcontainersConfiguration;
import com.corridor.office.audit.domain.AuditChain;
import com.corridor.office.audit.domain.AuditEntry;
import com.corridor.office.audit.domain.AuditEvent;
import com.corridor.office.audit.infrastructure.AuditEventRepository;
import com.corridor.office.common.domain.ConflictException;
import com.corridor.office.common.security.CurrentUser;
import com.corridor.office.common.security.Role;
import com.corridor.office.identity.application.AuthService;
import com.corridor.office.identity.application.InvalidCredentialsException;
import com.corridor.office.identity.application.TeamService;
import com.corridor.office.identity.application.UserAccessService;
import com.corridor.office.identity.application.UserChanges;
import com.corridor.office.identity.application.UserService;
import com.corridor.office.identity.domain.IdentityFixtures;
import com.corridor.office.identity.domain.Organization;
import com.corridor.office.identity.domain.User;
import com.corridor.office.identity.infrastructure.OrganizationRepository;
import com.corridor.office.identity.infrastructure.UserRepository;
import com.corridor.office.support.ConcurrentRuns;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@WithMockUser(roles = "ADMIN")
class AuditRecorderIntegrationTest {

    private static final String PASSWORD = "Welcome-pass-1";

    @Autowired
    UserService userService;

    @Autowired
    UserAccessService access;

    @Autowired
    TeamService teamService;

    @Autowired
    AuthService auth;

    @Autowired
    AuditRecorder recorder;

    @Autowired
    AuditEventRepository events;

    @Autowired
    OrganizationRepository organizations;

    @Autowired
    UserRepository users;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    TransactionTemplate transactions;

    @Autowired
    JdbcClient jdbc;

    private Organization organization;
    private CurrentUser actor;
    private User omar;

    @BeforeEach
    void createPeople() {
        organization = organizations.save(IdentityFixtures.organization());
        var admin = users.save(User.register(
                organization, "admin@corridor.local", "Muhammed Samer", Role.ADMIN, passwordEncoder.encode(PASSWORD)));
        omar = users.save(User.register(
                organization, "omar@corridor.local", "Omar Adel", Role.EMPLOYEE, passwordEncoder.encode(PASSWORD)));
        actor = new CurrentUser(admin.id(), organization.id(), Role.ADMIN);
    }

    @AfterEach
    void clearTables() {
        jdbc.sql("truncate audit_events, refresh_tokens, users, teams, organizations cascade")
                .update();
    }

    private List<AuditEvent> recorded() {
        return transactions.execute(status -> {
            try (var stored = events.streamByOrganizationIdOrderBySequence(organization.id())) {
                return stored.toList();
            }
        });
    }

    @Test
    void shouldRecordASuspensionWithWhoDidItAndToWhom() {
        access.suspend(actor, omar.id());

        assertThat(recorded()).singleElement().satisfies(event -> {
            assertThat(event.action()).isEqualTo("user.suspended");
            assertThat(event.actorId()).isEqualTo(actor.userId());
            assertThat(event.targetId()).isEqualTo(omar.id());
            assertThat(event.details())
                    .containsEntry("name", "Omar Adel")
                    .containsEntry("actorName", "Muhammed Samer")
                    .containsEntry("actorRole", "ADMIN");
        });
    }

    @Test
    void shouldChainEventsInTheOrderTheyHappened() {
        access.suspend(actor, omar.id());
        access.reactivate(actor, omar.id());

        var recorded = recorded();
        assertThat(recorded).extracting(AuditEvent::action).containsExactly("user.suspended", "user.reactivated");
        assertThat(AuditChain.verify(recorded.stream()).intact()).isTrue();
    }

    @Test
    void shouldRecordARoleChangeAndATeamMove() {
        var team = teamService.create(actor, "Product", "#2F7D6D");
        var changes = new UserChanges("Omar Adel", null, Role.MANAGER, team.id());

        userService.update(
                actor, omar.id(), users.findById(omar.id()).orElseThrow().version(), changes);

        var recorded = recorded();
        assertThat(recorded)
                .extracting(AuditEvent::action)
                .containsExactly("team.created", "user.role_changed", "user.team_changed");
        assertThat(recorded.get(1).details()).containsEntry("from", "EMPLOYEE").containsEntry("to", "MANAGER");
        assertThat(recorded.get(2).details()).containsEntry("to", "Product").doesNotContainKey("from");
    }

    @Test
    void shouldRecordTeamRenamesAndDeletes() {
        var team = teamService.create(actor, "Core platform", "#2F7D6D");
        var renamed = teamService.update(actor, team.id(), team.version(), "Platform", "#2F7D6D");
        teamService.delete(actor, renamed.id());

        var recorded = recorded();
        assertThat(recorded)
                .extracting(AuditEvent::action)
                .containsExactly("team.created", "team.renamed", "team.deleted");
        assertThat(recorded.get(1).details())
                .containsEntry("from", "Core platform")
                .containsEntry("to", "Platform");
    }

    @Test
    void shouldRecordAFailedSignInAndStillRefuseIt() {
        assertThatThrownBy(() -> auth.signIn("omar@corridor.local", "wrong-password", "41.33.12.8"))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThatThrownBy(() -> auth.signIn("ceo@corridor.local", PASSWORD, "41.33.12.8"))
                .isInstanceOf(InvalidCredentialsException.class);

        var recorded = recorded();
        assertThat(recorded).extracting(AuditEvent::action).containsOnly("auth.login_failed");
        assertThat(recorded.get(0).details())
                .containsEntry("email", "omar@corridor.local")
                .containsEntry("reason", "wrong_password")
                .containsEntry("ip", "41.33.12.8");
        assertThat(recorded.get(0).targetId()).isEqualTo(omar.id());
        assertThat(recorded.get(1).details()).containsEntry("reason", "unknown_email");
        assertThat(recorded.get(1).targetId()).isNull();
    }

    @Test
    void shouldNotRecordAnActionThatWasRefused() {
        assertThatThrownBy(() -> access.suspend(actor, actor.userId())).isInstanceOf(ConflictException.class);

        assertThat(recorded()).isEmpty();
    }

    @Test
    void shouldKeepOneChainWhenTwoEventsAreRecordedConcurrently() throws Exception {
        var entry = new AuditEntry(organization.id(), actor.userId(), "user.suspended", "user", omar.id(), Map.of());

        var outcomes =
                ConcurrentRuns.runTwiceConcurrently(() -> transactions.execute(status -> recorder.record(entry)));

        assertThat(outcomes.failures()).isEmpty();
        var recorded = recorded();
        assertThat(recorded).extracting(AuditEvent::sequence).containsExactly(1L, 2L);
        assertThat(AuditChain.verify(recorded.stream()).intact()).isTrue();
    }

    @Test
    void shouldRefuseToRecordOutsideATransaction() {
        var entry = new AuditEntry(organization.id(), null, "user.suspended", "user", UUID.randomUUID(), Map.of());

        assertThatThrownBy(() -> recorder.record(entry)).isInstanceOf(IllegalTransactionStateException.class);
    }
}
