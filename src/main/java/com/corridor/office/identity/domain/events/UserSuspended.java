package com.corridor.office.identity.domain.events;

import java.util.UUID;

public record UserSuspended(UUID userId, UUID organizationId, UUID actorId) {}
