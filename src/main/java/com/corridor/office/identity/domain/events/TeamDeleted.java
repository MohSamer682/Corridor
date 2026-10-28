package com.corridor.office.identity.domain.events;

import java.util.UUID;

public record TeamDeleted(UUID teamId, UUID organizationId, UUID actorId, String name) {}
