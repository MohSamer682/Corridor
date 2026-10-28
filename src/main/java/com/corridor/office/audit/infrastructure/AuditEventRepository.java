package com.corridor.office.audit.infrastructure;

import com.corridor.office.audit.domain.AuditEvent;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    Optional<AuditEvent> findFirstByOrganizationIdOrderBySequenceDesc(UUID organizationId);

    Stream<AuditEvent> streamByOrganizationIdOrderBySequence(UUID organizationId);

    /** Holds the organization's chain until the transaction ends, so concurrent writers link one after another. */
    @Query(
            value =
                    "SELECT count(*) FROM (SELECT pg_advisory_xact_lock(hashtextextended(cast(:organizationId AS text), 0))) AS locked",
            nativeQuery = true)
    long lockChain(UUID organizationId);
}
