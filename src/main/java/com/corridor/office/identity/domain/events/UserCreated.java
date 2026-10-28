package com.corridor.office.identity.domain.events;

import java.util.UUID;

public record UserCreated(UUID userId, UUID organizationId, UUID actorId) {}
