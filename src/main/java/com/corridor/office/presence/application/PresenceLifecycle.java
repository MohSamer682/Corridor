package com.corridor.office.presence.application;

import com.corridor.office.identity.domain.events.UserRemoved;
import com.corridor.office.identity.domain.events.UserSuspended;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/** Keeps presence in step with sockets opening and closing, and with people losing access. */
@Component
class PresenceLifecycle {

    private final PresenceService presence;

    PresenceLifecycle(PresenceService presence) {
        this.presence = presence;
    }

    @EventListener
    void on(SessionConnectedEvent event) {
        var user = event.getUser();
        if (user != null) {
            presence.connected(
                    UUID.fromString(user.getName()),
                    StompHeaderAccessor.wrap(event.getMessage()).getSessionId());
        }
    }

    @EventListener
    void on(SessionDisconnectEvent event) {
        var user = event.getUser();
        if (user != null) {
            presence.disconnected(UUID.fromString(user.getName()), event.getSessionId());
        }
    }

    // After commit: a suspension that rolls back must not throw anyone out.
    @TransactionalEventListener
    void on(UserSuspended event) {
        presence.evict(event.userId());
    }

    @TransactionalEventListener
    void on(UserRemoved event) {
        presence.evict(event.userId());
    }
}
