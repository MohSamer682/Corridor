package com.corridor.office.identity.domain.events;

import java.util.UUID;

public record UserReactivated(UUID userId, UUID organizationId, UUID actorId, String name) {}
