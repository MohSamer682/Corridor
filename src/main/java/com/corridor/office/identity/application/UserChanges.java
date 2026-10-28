package com.corridor.office.identity.application;

import com.corridor.office.common.security.Role;
import java.util.UUID;

public record UserChanges(String displayName, String jobTitle, Role role, UUID teamId) {}
