package com.corridor.office.conversations.application;

import com.corridor.office.common.domain.ConflictException;
import com.corridor.office.common.security.CurrentUser;
import com.corridor.office.conversations.domain.MediaRoute;
import com.corridor.office.identity.UserDirectory;
import com.corridor.office.meetings.MediaTokens;
import com.corridor.office.meetings.MediaTokens.Join;
import java.time.Clock;
import java.time.Duration;
import org.springframework.stereotype.Service;

/** A LiveKit ticket for a conversation too big to run peer to peer; the room is named after the group. */
@Service
public class GroupMedia {

    private static final Duration LIFETIME = Duration.ofHours(1);

    private final ConversationService conversations;
    private final MediaTokens tokens;
    private final UserDirectory directory;
    private final Clock clock;

    GroupMedia(ConversationService conversations, MediaTokens tokens, UserDirectory directory, Clock clock) {
        this.conversations = conversations;
        this.tokens = tokens;
        this.directory = directory;
        this.clock = clock;
    }

    public Join join(CurrentUser person) {
        var group = conversations
                .groupOf(person.userId())
                .filter(g -> MediaRoute.forGroup(g) instanceof MediaRoute.Sfu)
                .orElseThrow(() -> new ConflictException("conversations.notSfu"));
        var name = directory
                .find(person.userId())
                .map(UserDirectory.Person::displayName)
                .orElse("");
        return tokens.issue(
                "group-" + group.id(), person.userId(), name, clock.instant().plus(LIFETIME));
    }
}
