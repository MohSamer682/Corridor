package com.corridor.office.audit.infrastructure;

import com.corridor.office.audit.domain.AuditEvent;
import java.time.Instant;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

public final class AuditEventSpecifications {

    private static final String CREATED_AT = "createdAt";

    private AuditEventSpecifications() {}

    public static Specification<AuditEvent> inOrganization(UUID organizationId) {
        return (root, query, cb) -> cb.equal(root.get("organizationId"), organizationId);
    }

    public static Specification<AuditEvent> actionIn(Collection<String> actions) {
        return (root, query, cb) -> root.get("action").in(actions);
    }

    public static Specification<AuditEvent> byActor(UUID actorId) {
        return (root, query, cb) -> cb.equal(root.get("actorId"), actorId);
    }

    public static Specification<AuditEvent> notBefore(Instant from) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get(CREATED_AT), from);
    }

    public static Specification<AuditEvent> before(Instant to) {
        return (root, query, cb) -> cb.lessThan(root.get(CREATED_AT), to);
    }

    /** Keyset step for newest-first paging on {@code (createdAt, id)}. */
    public static Specification<AuditEvent> olderThan(Instant createdAt, UUID id) {
        return (root, query, cb) -> cb.or(
                cb.lessThan(root.get(CREATED_AT), createdAt),
                cb.and(cb.equal(root.get(CREATED_AT), createdAt), cb.lessThan(root.get("id"), id)));
    }
}
