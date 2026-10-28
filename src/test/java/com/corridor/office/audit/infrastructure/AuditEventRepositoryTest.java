package com.corridor.office.audit.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.corridor.office.TestcontainersConfiguration;
import com.corridor.office.audit.domain.AuditChain;
import com.corridor.office.audit.domain.AuditEntry;
import com.corridor.office.audit.domain.AuditEvent;
import com.corridor.office.config.JpaAuditingConfig;
import com.corridor.office.identity.domain.IdentityFixtures;
import com.corridor.office.identity.infrastructure.OrganizationRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AuditEventRepositoryTest {

    private static final Instant AT = Instant.parse("2026-10-28T12:00:00.123456789Z");

    @Autowired
    AuditEventRepository events;

    @Autowired
    OrganizationRepository organizations;

    @Autowired
    EntityManager entityManager;

    private UUID organizationId;

    @BeforeEach
    void saveOrganization() {
        organizationId =
                organizations.saveAndFlush(IdentityFixtures.organization()).id();
    }

    private AuditEvent append(AuditEvent previous, String name) {
        var entry = new AuditEntry(
                organizationId, UUID.randomUUID(), "user.suspended", "user", UUID.randomUUID(), Map.of("name", name));
        return events.saveAndFlush(AuditEvent.append(previous, entry, AT));
    }

    @Test
    void shouldReadBackAChainThatStillVerifies() {
        var first = append(null, "Omar Adel");
        append(first, "Nour Hassan");
        entityManager.clear();

        try (var stored = events.streamByOrganizationIdOrderBySequence(organizationId)) {
            var check = AuditChain.verify(stored);

            assertThat(check.checked()).isEqualTo(2);
            assertThat(check.intact()).isTrue();
        }
    }

    @Test
    void shouldFindTheLatestEventOfTheOrganization() {
        var first = append(null, "Omar Adel");
        var second = append(first, "Nour Hassan");

        assertThat(events.findFirstByOrganizationIdOrderBySequenceDesc(organizationId))
                .map(AuditEvent::id)
                .contains(second.id());
    }

    @Test
    void shouldRefuseToUpdateAnEvent() {
        var event = append(null, "Omar Adel");

        assertThatThrownBy(() -> entityManager
                        .createNativeQuery("UPDATE audit_events SET details = '{}' WHERE id = :id")
                        .setParameter("id", event.id())
                        .executeUpdate())
                .hasStackTraceContaining("audit_events is append-only");
    }

    @Test
    void shouldRefuseToDeleteAnEvent() {
        var event = append(null, "Omar Adel");

        assertThatThrownBy(() -> entityManager
                        .createNativeQuery("DELETE FROM audit_events WHERE id = :id")
                        .setParameter("id", event.id())
                        .executeUpdate())
                .hasStackTraceContaining("audit_events is append-only");
    }

    @Test
    void shouldRejectTwoEventsWithTheSameSequence() {
        append(null, "Omar Adel");

        assertThatThrownBy(() -> append(null, "Nour Hassan"))
                .hasStackTraceContaining("uq_audit_events_organization_id_sequence");
    }
}
