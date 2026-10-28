package com.corridor.office.identity.application;

import com.corridor.office.common.security.Role;
import com.corridor.office.identity.domain.UserStatus;
import java.util.UUID;

public record UserSummary(
        UUID id,
        String email,
        String displayName,
        String jobTitle,
        Role role,
        UserStatus status,
        UUID teamId,
        String teamName) {}
