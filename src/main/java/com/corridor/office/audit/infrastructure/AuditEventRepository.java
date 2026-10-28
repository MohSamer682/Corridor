package com.corridor.office.audit.infrastructure;

import com.corridor.office.audit.domain.AuditEvent;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    Optional<AuditEvent> findFirstByOrganizationIdOrderBySequenceDesc(UUID organizationId);

    Stream<AuditEvent> streamByOrganizationIdOrderBySequence(UUID organizationId);
}
