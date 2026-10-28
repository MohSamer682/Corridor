package com.corridor.office.identity.domain.events;

import com.corridor.office.common.security.Role;
import java.util.UUID;

/** {@code teamName} is null when the person joins without a team. */
public record UserApproved(UUID userId, UUID organizationId, UUID actorId, String name, Role role, String teamName) {}
