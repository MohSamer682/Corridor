package com.corridor.office.audit.application;

import static com.corridor.office.audit.infrastructure.AuditEventSpecifications.actionIn;
import static com.corridor.office.audit.infrastructure.AuditEventSpecifications.before;
import static com.corridor.office.audit.infrastructure.AuditEventSpecifications.byActor;
import static com.corridor.office.audit.infrastructure.AuditEventSpecifications.inOrganization;
import static com.corridor.office.audit.infrastructure.AuditEventSpecifications.notBefore;
import static com.corridor.office.audit.infrastructure.AuditEventSpecifications.olderThan;

import com.corridor.office.audit.domain.AuditChain;
import com.corridor.office.audit.domain.AuditEvent;
import com.corridor.office.audit.infrastructure.AuditEventRepository;
import com.corridor.office.common.api.CursorResponse;
import com.corridor.office.common.security.CurrentUser;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@PreAuthorize("hasAnyRole('ADMIN', 'HR')")
public class AuditQuery {

    private static final int MAX_SIZE = 100;
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt", "id");
    private static final Map<String, List<String>> GROUPS = Map.of(
            "people",
            List.of(
                    "user.created",
                    "user.approved",
                    "user.declined",
                    "user.suspended",
                    "user.reactivated",
                    "user.removed",
                    "user.role_changed"),
            "teams",
            List.of("user.team_changed", "team.created", "team.renamed", "team.recolored", "team.deleted"),
            "sign-in",
            List.of("auth.login_failed"));

    private final AuditEventRepository events;

    AuditQuery(AuditEventRepository events) {
        this.events = events;
    }

    @Transactional(readOnly = true)
    public CursorResponse<AuditEventView> list(CurrentUser viewer, AuditFilter filter, String cursor, int size) {
        var limit = Math.clamp(size, 1, MAX_SIZE);
        var found = events.findBy(
                Specification.allOf(conditions(viewer, filter, cursor)),
                query -> query.sortBy(NEWEST_FIRST).limit(limit + 1).all());
        var page = found.stream().limit(limit).toList();
        var next = found.size() > limit ? cursorAfter(page.getLast()) : null;
        return new CursorResponse<>(page.stream().map(AuditEventView::of).toList(), next);
    }

    // shortcut: walks the whole chain on every call; keep a verified-up-to checkpoint once logs reach millions of rows.
    @Transactional(readOnly = true)
    public ChainVerification verify(CurrentUser viewer) {
        try (var chain = events.streamByOrganizationIdOrderBySequence(viewer.organizationId())) {
            var check = AuditChain.verify(chain);
            return check.firstAltered()
                    .map(altered -> new ChainVerification(false, check.checked(), altered.id(), altered.createdAt()))
                    .orElseGet(() -> new ChainVerification(true, check.checked(), null, null));
        }
    }

    private static List<Specification<AuditEvent>> conditions(CurrentUser viewer, AuditFilter filter, String cursor) {
        if (filter.from() != null && filter.to() != null && !filter.from().isBefore(filter.to())) {
            throw new InvalidAuditQueryException("audit.badRange");
        }
        var conditions = new ArrayList<Specification<AuditEvent>>();
        conditions.add(inOrganization(viewer.organizationId()));
        if (filter.action() != null) {
            conditions.add(actionIn(GROUPS.getOrDefault(filter.action(), List.of(filter.action()))));
        }
        if (filter.actorId() != null) {
            conditions.add(byActor(filter.actorId()));
        }
        if (filter.from() != null) {
            conditions.add(notBefore(filter.from()));
        }
        if (filter.to() != null) {
            conditions.add(before(filter.to()));
        }
        if (cursor != null) {
            var position = AuditCursor.decode(cursor);
            conditions.add(olderThan(position.createdAt(), position.id()));
        }
        return conditions;
    }

    private static String cursorAfter(AuditEvent last) {
        return new AuditCursor(last.createdAt(), last.id()).encode();
    }
}
