package com.corridor.office.identity.application;

import com.corridor.office.common.security.Role;
import java.util.UUID;

public record NewUser(String email, String displayName, String jobTitle, Role role, UUID teamId, String password) {}
