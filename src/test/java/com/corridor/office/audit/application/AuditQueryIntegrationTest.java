package com.corridor.office.audit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.corridor.office.TestcontainersConfiguration;
import com.corridor.office.audit.domain.AuditEntry;
import com.corridor.office.common.security.CurrentUser;
import com.corridor.office.common.security.Role;
import com.corridor.office.identity.domain.IdentityFixtures;
import com.corridor.office.identity.infrastructure.OrganizationRepository;
import com.corridor.office.support.MutableClock;
import com.corridor.office.support.MutableClockConfiguration;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import({TestcontainersConfiguration.class, MutableClockConfiguration.class})
@ActiveProfiles("test")
@WithMockUser(roles = "ADMIN")
class AuditQueryIntegrationTest {

    private static final AuditFilter EVERYTHING = new AuditFilter(null, null, null, null);

    @Autowired
    AuditQuery query;

    @Autowired
    AuditRecorder recorder;

    @Autowired
    OrganizationRepository organizations;

    @Autowired
    TransactionTemplate transactions;

    @Autowired
    MutableClock clock;

    @Autowired
    JdbcClient jdbc;

    private UUID organizationId;
    private CurrentUser viewer;
    private final UUID admin = UUID.randomUUID();
    private final UUID hr = UUID.randomUUID();

    @BeforeEach
    void createOrganization() {
        organizationId = organizations.save(IdentityFixtures.organization()).id();
        viewer = new CurrentUser(admin, organizationId, Role.ADMIN);
    }

    @AfterEach
    void clearTables() {
        jdbc.sql("truncate audit_events, organizations cascade").update();
    }

    private Instant record(UUID actor, String action) {
        clock.advance(Duration.ofMinutes(1));
        var entry = new AuditEntry(organizationId, actor, action, "user", UUID.randomUUID(), Map.of("name", "Omar"));
        return transactions.execute(status -> recorder.record(entry)).createdAt();
    }

    @Test
    void shouldListNewestFirstAndPageWithACursor() {
        record(admin, "user.created");
        record(admin, "user.suspended");
        record(admin, "user.reactivated");

        var first = query.list(viewer, EVERYTHING, null, 2);
        var second = query.list(viewer, EVERYTHING, first.nextCursor(), 2);

        assertThat(first.items())
                .extracting(AuditEventView::action)
                .containsExactly("user.reactivated", "user.suspended");
        assertThat(second.items()).extracting(AuditEventView::action).containsExactly("user.created");
        assertThat(second.nextCursor()).isNull();
    }

    @Test
    void shouldFilterByGroupActionActorAndTime() {
        record(admin, "user.suspended");
        var teamCreatedAt = record(hr, "team.created");
        record(null, "auth.login_failed");
        record(admin, "user.team_changed");

        assertThat(query.list(viewer, new AuditFilter("teams", null, null, null), null, 50)
                        .items())
                .extracting(AuditEventView::action)
                .containsExactly("user.team_changed", "team.created");
        assertThat(query.list(viewer, new AuditFilter("sign-in", null, null, null), null, 50)
                        .items())
                .extracting(AuditEventView::action)
                .containsExactly("auth.login_failed");
        assertThat(query.list(viewer, new AuditFilter("user.suspended", null, null, null), null, 50)
                        .items())
                .hasSize(1);
        assertThat(query.list(viewer, new AuditFilter(null, hr, null, null), null, 50)
                        .items())
                .extracting(AuditEventView::action)
                .containsExactly("team.created");
        assertThat(query.list(
                                viewer,
                                new AuditFilter(null, null, teamCreatedAt, teamCreatedAt.plusSeconds(1)),
                                null,
                                50)
                        .items())
                .extracting(AuditEventView::action)
                .containsExactly("team.created");
    }

    @Test
    void shouldNotShowAnotherOrganizationsEvents() {
        record(admin, "user.suspended");
        var stranger = new CurrentUser(admin, UUID.randomUUID(), Role.ADMIN);

        assertThat(query.list(stranger, EVERYTHING, null, 50).items()).isEmpty();
        assertThat(query.verify(stranger).checked()).isZero();
    }

    @Test
    void shouldRejectABrokenCursorAndABackwardsRange() {
        var now = Instant.now();

        assertThatThrownBy(() -> query.list(viewer, EVERYTHING, "not-a-cursor", 50))
                .isInstanceOf(InvalidAuditQueryException.class);
        assertThatThrownBy(() -> query.list(viewer, new AuditFilter(null, null, now, now.minusSeconds(60)), null, 50))
                .isInstanceOf(InvalidAuditQueryException.class);
    }

    @Test
    void shouldReportTheFirstAlteredEvent() {
        record(admin, "user.created");
        record(admin, "user.suspended");
        record(admin, "user.reactivated");
        transactions.executeWithoutResult(status -> {
            jdbc.sql("ALTER TABLE audit_events DISABLE TRIGGER audit_events_append_only")
                    .update();
            jdbc.sql("UPDATE audit_events SET details = '{\"name\": \"Someone else\"}' WHERE sequence = 2")
                    .update();
            jdbc.sql("ALTER TABLE audit_events ENABLE TRIGGER audit_events_append_only")
                    .update();
        });

        var verification = query.verify(viewer);

        assertThat(verification.intact()).isFalse();
        assertThat(verification.checked()).isEqualTo(2);
        assertThat(verification.firstAlteredId()).isNotNull();
    }

    @Test
    void shouldReportAnIntactLog() {
        record(admin, "user.created");
        record(admin, "user.suspended");

        assertThat(query.verify(viewer)).satisfies(verification -> {
            assertThat(verification.intact()).isTrue();
            assertThat(verification.checked()).isEqualTo(2);
            assertThat(verification.firstAlteredId()).isNull();
        });
    }

    @Test
    @WithMockUser(roles = "HR")
    void shouldLetHrReadTheLog() {
        record(admin, "user.suspended");

        assertThat(query.list(new CurrentUser(hr, organizationId, Role.HR), EVERYTHING, null, 50)
                        .items())
                .hasSize(1);
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void shouldRefuseManagers() {
        var manager = new CurrentUser(UUID.randomUUID(), organizationId, Role.MANAGER);

        assertThatThrownBy(() -> query.list(manager, EVERYTHING, null, 50)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> query.verify(manager)).isInstanceOf(AccessDeniedException.class);
    }
}
