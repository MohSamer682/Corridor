package com.corridor.office.identity.domain.events;

import com.corridor.office.common.security.Role;
import java.util.UUID;

public record UserCreated(UUID userId, UUID organizationId, UUID actorId, String name, String email, Role role) {}
