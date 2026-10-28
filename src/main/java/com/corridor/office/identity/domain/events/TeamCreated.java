package com.corridor.office.identity.domain.events;

import java.util.UUID;

public record TeamCreated(UUID teamId, UUID organizationId, UUID actorId, String name) {}
