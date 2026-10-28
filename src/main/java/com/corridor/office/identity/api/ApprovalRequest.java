package com.corridor.office.identity.api;

import com.corridor.office.common.security.Role;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

record ApprovalRequest(@NotNull(message = "Pick a role.") Role role, UUID teamId) {}
