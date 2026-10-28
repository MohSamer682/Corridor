package com.corridor.office.audit.application;

import com.corridor.office.audit.domain.AuditEntry;
import com.corridor.office.identity.UserDirectory;
import com.corridor.office.identity.domain.events.RoleChanged;
import com.corridor.office.identity.domain.events.SignInFailed;
import com.corridor.office.identity.domain.events.TeamCreated;
import com.corridor.office.identity.domain.events.TeamDeleted;
import com.corridor.office.identity.domain.events.TeamRecolored;
import com.corridor.office.identity.domain.events.TeamRenamed;
import com.corridor.office.identity.domain.events.UserApproved;
import com.corridor.office.identity.domain.events.UserCreated;
import com.corridor.office.identity.domain.events.UserDeclined;
import com.corridor.office.identity.domain.events.UserReactivated;
import com.corridor.office.identity.domain.events.UserRemoved;
import com.corridor.office.identity.domain.events.UserSuspended;
import com.corridor.office.identity.domain.events.UserTeamChanged;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Turns identity events into audit entries, inside the transaction that published them. */
@Component
class IdentityAuditListener {

    private static final String USER = "user";
    private static final String TEAM = "team";
    private static final String NAME = "name";

    private final AuditRecorder recorder;
    private final UserDirectory directory;

    IdentityAuditListener(AuditRecorder recorder, UserDirectory directory) {
        this.recorder = recorder;
        this.directory = directory;
    }

    @EventListener
    void on(UserCreated event) {
        var details = details(
                NAME, event.name(), "email", event.email(), "role", event.role().name());
        record(new AuditEntry(event.organizationId(), event.actorId(), "user.created", USER, event.userId(), details));
    }

    @EventListener
    void on(UserApproved event) {
        var details = details(NAME, event.name(), "role", event.role().name(), "team", event.teamName());
        record(new AuditEntry(event.organizationId(), event.actorId(), "user.approved", USER, event.userId(), details));
    }

    @EventListener
    void on(UserDeclined event) {
        var details = details(NAME, event.name(), "email", event.email());
        record(new AuditEntry(event.organizationId(), event.actorId(), "user.declined", USER, event.userId(), details));
    }

    @EventListener
    void on(UserSuspended event) {
        var details = details(NAME, event.name());
        record(new AuditEntry(
                event.organizationId(), event.actorId(), "user.suspended", USER, event.userId(), details));
    }

    @EventListener
    void on(UserReactivated event) {
        var details = details(NAME, event.name());
        record(new AuditEntry(
                event.organizationId(), event.actorId(), "user.reactivated", USER, event.userId(), details));
    }

    @EventListener
    void on(UserRemoved event) {
        var details = details(NAME, event.name());
        record(new AuditEntry(event.organizationId(), event.actorId(), "user.removed", USER, event.userId(), details));
    }

    @EventListener
    void on(RoleChanged event) {
        var details = details(
                NAME,
                event.name(),
                "from",
                event.from().name(),
                "to",
                event.to().name());
        record(new AuditEntry(
                event.organizationId(), event.actorId(), "user.role_changed", USER, event.userId(), details));
    }

    @EventListener
    void on(UserTeamChanged event) {
        var details = details(NAME, event.name(), "from", event.fromTeam(), "to", event.toTeam());
        record(new AuditEntry(
                event.organizationId(), event.actorId(), "user.team_changed", USER, event.userId(), details));
    }

    @EventListener
    void on(TeamCreated event) {
        var details = details(NAME, event.name());
        record(new AuditEntry(event.organizationId(), event.actorId(), "team.created", TEAM, event.teamId(), details));
    }

    @EventListener
    void on(TeamRenamed event) {
        var details = details(NAME, event.to(), "from", event.from(), "to", event.to());
        record(new AuditEntry(event.organizationId(), event.actorId(), "team.renamed", TEAM, event.teamId(), details));
    }

    @EventListener
    void on(TeamRecolored event) {
        var details = details(NAME, event.name(), "from", event.from(), "to", event.to());
        record(new AuditEntry(
                event.organizationId(), event.actorId(), "team.recolored", TEAM, event.teamId(), details));
    }

    @EventListener
    void on(TeamDeleted event) {
        var details = details(NAME, event.name());
        record(new AuditEntry(event.organizationId(), event.actorId(), "team.deleted", TEAM, event.teamId(), details));
    }

    @EventListener
    void on(SignInFailed event) {
        var reason = event.reason().name().toLowerCase(Locale.ROOT);
        var details = details("email", event.email(), "reason", reason, "ip", event.clientIp());
        record(new AuditEntry(event.organizationId(), null, "auth.login_failed", USER, event.userId(), details));
    }

    private void record(AuditEntry entry) {
        var actor =
                entry.actorId() == null ? null : directory.find(entry.actorId()).orElse(null);
        recorder.record(
                actor == null
                        ? entry
                        : entry.withActor(actor.displayName(), actor.role().name()));
    }

    // Skips null values, so "no team" is a missing key rather than a stored null.
    private static Map<String, String> details(String... keysAndValues) {
        var details = new HashMap<String, String>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            if (keysAndValues[i + 1] != null) {
                details.put(keysAndValues[i], keysAndValues[i + 1]);
            }
        }
        return details;
    }
}
