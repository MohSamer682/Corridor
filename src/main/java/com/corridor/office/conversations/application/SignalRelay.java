package com.corridor.office.conversations.application;

import com.corridor.office.common.domain.InvalidInputException;
import com.corridor.office.common.security.CurrentUser;
import com.corridor.office.conversations.domain.MediaRoute;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/** Passes WebRTC offers, answers and ICE candidates, but only between members of one peer-to-peer group. */
@Service
public class SignalRelay {

    public static final int MAX_PAYLOAD = 64 * 1024;
    private static final Set<String> KINDS = Set.of("offer", "answer", "candidate");
    private static final Logger log = LoggerFactory.getLogger(SignalRelay.class);

    public record SignalView(UUID from, String kind, String payload) {}

    private final ConversationService conversations;
    private final SimpMessagingTemplate sockets;

    SignalRelay(ConversationService conversations, SimpMessagingTemplate sockets) {
        this.conversations = conversations;
        this.sockets = sockets;
    }

    public void relay(CurrentUser sender, UUID to, String kind, String payload) {
        if (!KINDS.contains(kind) || payload == null || payload.length() > MAX_PAYLOAD) {
            throw new InvalidInputException("conversations.badSignal");
        }
        var group = conversations.groupOf(sender.userId());
        var allowed = group.isPresent()
                && group.get().has(to)
                && !to.equals(sender.userId())
                && MediaRoute.forGroup(group.get()).relaysSignals();
        if (!allowed) {
            log.debug("Dropped a {} from {} to {}: not in one peer-to-peer group", kind, sender.userId(), to);
            return;
        }
        sockets.convertAndSendToUser(to.toString(), "/queue/signals", new SignalView(sender.userId(), kind, payload));
    }
}
