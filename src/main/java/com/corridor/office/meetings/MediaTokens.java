package com.corridor.office.meetings;

import java.time.Instant;
import java.util.UUID;

/** Tickets into the SFU (LiveKit) for meetings and for conversations too big to run peer to peer. */
public interface MediaTokens {

    /** Lets {@code userId} publish and listen in {@code room} until {@code expiresAt}. */
    Join issue(String room, UUID userId, String displayName, Instant expiresAt);

    /** Where to connect and the token to connect with. */
    record Join(String url, String token) {}
}
