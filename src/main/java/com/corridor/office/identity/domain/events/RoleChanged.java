package com.corridor.office.identity.domain.events;

import com.corridor.office.common.security.Role;
import java.util.UUID;

public record RoleChanged(UUID userId, UUID organizationId, UUID actorId, Role from, Role to) {}
