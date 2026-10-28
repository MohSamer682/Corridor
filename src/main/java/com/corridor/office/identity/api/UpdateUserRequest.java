package com.corridor.office.identity.api;

import com.corridor.office.common.security.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

record UpdateUserRequest(
        @NotBlank(message = "Use 2 to 80 characters.") @Size(min = 2, max = 80, message = "Use 2 to 80 characters.")
                String displayName,
        @Size(max = 80, message = "Use up to 80 characters.") String jobTitle,
        @NotNull(message = "Pick a role.") Role role,
        UUID teamId) {

    UpdateUserRequest {
        displayName = displayName == null ? null : displayName.strip();
    }
}
