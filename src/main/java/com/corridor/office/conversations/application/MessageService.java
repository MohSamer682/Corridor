package com.corridor.office.conversations.application;

import com.corridor.office.common.api.CursorResponse;
import com.corridor.office.common.domain.ConflictException;
import com.corridor.office.common.domain.InvalidInputException;
import com.corridor.office.common.security.CurrentUser;
import com.corridor.office.conversations.domain.Message;
import com.corridor.office.conversations.infrastructure.MessageRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/** Chat inside a conversation group. Recipients are fixed by the server when the message is sent. */
@Service
public class MessageService {

    public static final int MAX_LENGTH = 2_000;
    private static final int PAGE = 50;

    public record MessageView(UUID id, UUID senderId, String body, Instant createdAt) {

        static MessageView of(Message message) {
            return new MessageView(message.id(), message.senderId(), message.body(), message.createdAt());
        }
    }

    private final ConversationService conversations;
    private final MessageRepository messages;
    private final SimpMessagingTemplate sockets;

    MessageService(ConversationService conversations, MessageRepository messages, SimpMessagingTemplate sockets) {
        this.conversations = conversations;
        this.messages = messages;
        this.sockets = sockets;
    }

    /** Stores the message and delivers it to everyone in the sender's group who isn't in focus. */
    public MessageView send(CurrentUser sender, String body) {
        var text = body == null ? "" : body.strip();
        if (text.isEmpty() || text.length() > MAX_LENGTH) {
            throw new InvalidInputException("conversations.bodyLength");
        }
        var group = conversations
                .groupOf(sender.userId())
                .orElseThrow(() -> new ConflictException("conversations.nobodyNear"));
        var floorId = conversations
                .floorOf(sender.userId())
                .orElseThrow(() -> new ConflictException("conversations.nobodyNear"));
        // Focus can turn on after the group was built; check again right before delivering.
        var recipients = group.members().stream()
                .filter(id -> id.equals(sender.userId()) || !conversations.inFocus(id))
                .toList();
        var view = MessageView.of(
                messages.save(new Message(sender.organizationId(), floorId, sender.userId(), recipients, text)));
        recipients.forEach(id -> sockets.convertAndSendToUser(id.toString(), "/queue/messages", view));
        return view;
    }

    /** Messages you received on a floor, newest first, 50 at a time. */
    public CursorResponse<MessageView> history(CurrentUser reader, UUID floorId, String before) {
        var found = before == null
                ? messages.newest(reader.organizationId(), floorId, reader.userId(), PAGE + 1)
                : messages.before(reader.organizationId(), floorId, reader.userId(), cursor(before), PAGE + 1);
        var page = found.stream().limit(PAGE).map(MessageView::of).toList();
        var next = found.size() > PAGE ? page.getLast().id().toString() : null;
        return new CursorResponse<>(page, next);
    }

    private static UUID cursor(String before) {
        try {
            return UUID.fromString(before);
        } catch (IllegalArgumentException broken) {
            throw new InvalidInputException("conversations.badCursor");
        }
    }
}
