package com.corridor.office.identity.domain.events;

import java.util.UUID;

public record UserRemoved(UUID userId, UUID organizationId, UUID actorId, String name) {}
