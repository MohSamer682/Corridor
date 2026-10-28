package com.corridor.office.audit.application;

import com.corridor.office.audit.domain.AuditEntry;
import com.corridor.office.audit.domain.AuditEvent;
import com.corridor.office.audit.infrastructure.AuditEventRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditRecorder {

    private final AuditEventRepository events;
    private final Clock clock;

    AuditRecorder(AuditEventRepository events, Clock clock) {
        this.events = events;
        this.clock = clock;
    }

    // MANDATORY: an audit row commits with the change it records, or not at all.
    @Transactional(propagation = Propagation.MANDATORY)
    public AuditEvent record(AuditEntry entry) {
        events.lockChain(entry.organizationId());
        var latest = events.findFirstByOrganizationIdOrderBySequenceDesc(entry.organizationId());
        return events.save(AuditEvent.append(latest.orElse(null), entry, clock.instant()));
    }
}
