package com.corridor.office.identity.domain.events;

import java.util.UUID;

public record TeamRecolored(UUID teamId, UUID organizationId, UUID actorId, String name, String from, String to) {}
