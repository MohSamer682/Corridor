package com.corridor.office.audit.application;

import com.corridor.office.audit.domain.AuditEntry;
import com.corridor.office.identity.UserDirectory;
import com.corridor.office.workboard.events.JiraConnected;
import java.util.Map;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Records who connected Jira and to which site, inside the transaction that connected it. */
@Component
class WorkboardAuditListener {

    private final AuditRecorder recorder;
    private final UserDirectory directory;

    WorkboardAuditListener(AuditRecorder recorder, UserDirectory directory) {
        this.recorder = recorder;
        this.directory = directory;
    }

    @EventListener
    void on(JiraConnected event) {
        var entry = new AuditEntry(
                event.organizationId(),
                event.actorId(),
                "integration.jira_connected",
                "integration",
                null,
                Map.of("site", event.siteUrl(), "project", event.projectKey()));
        recorder.record(directory
                .find(entry.actorId())
                .map(actor -> entry.withActor(actor.displayName(), actor.role().name()))
                .orElse(entry));
    }
}
