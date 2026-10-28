package com.corridor.office.identity.domain.events;

import java.util.UUID;

/** {@code userId} is null when no account has the email that was tried. */
public record SignInFailed(UUID organizationId, UUID userId, String email, Reason reason, String clientIp) {

    public enum Reason {
        UNKNOWN_EMAIL,
        WRONG_PASSWORD,
        PENDING,
        SUSPENDED
    }
}
