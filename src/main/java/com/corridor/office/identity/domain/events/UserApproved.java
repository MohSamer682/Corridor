package com.corridor.office.identity.domain.events;

import com.corridor.office.common.security.Role;
import java.util.UUID;

public record UserApproved(UUID userId, UUID organizationId, UUID actorId, Role role) {}
