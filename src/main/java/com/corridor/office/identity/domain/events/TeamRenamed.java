package com.corridor.office.identity.domain.events;

import java.util.UUID;

public record TeamRenamed(UUID teamId, UUID organizationId, UUID actorId, String from, String to) {}
