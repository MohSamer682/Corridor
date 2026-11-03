package com.corridor.office.presence.infrastructure;

import com.corridor.office.presence.application.OpenSockets;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

/** Every open WebSocket by id; with plain WebSocket transport the STOMP session id is the same id. */
@Component
class SocketRegistry implements OpenSockets {

    private static final Logger log = LoggerFactory.getLogger(SocketRegistry.class);

    private final Map<String, WebSocketSession> open = new ConcurrentHashMap<>();

    void opened(WebSocketSession session) {
        open.put(session.getId(), session);
    }

    void closed(WebSocketSession session) {
        open.remove(session.getId());
    }

    @Override
    public void close(Collection<String> socketIds) {
        for (var id : socketIds) {
            var session = open.remove(id);
            if (session == null) {
                continue;
            }
            try {
                session.close(CloseStatus.POLICY_VIOLATION.withReason("Access removed"));
            } catch (IOException alreadyGone) {
                log.debug("Socket {} was already closing", id, alreadyGone);
            }
        }
    }
}
