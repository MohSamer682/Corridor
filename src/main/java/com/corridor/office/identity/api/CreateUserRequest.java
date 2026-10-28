package com.corridor.office.identity.api;

import com.corridor.office.common.security.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

record CreateUserRequest(
        @NotBlank(message = "Enter a work email.")
                @Email(message = "Enter a work email.")
                @Size(max = 254, message = "Enter a work email.")
                String email,
        @NotBlank(message = "Use 2 to 80 characters.") @Size(min = 2, max = 80, message = "Use 2 to 80 characters.")
                String displayName,
        @Size(max = 80, message = "Use up to 80 characters.") String jobTitle,
        @NotNull(message = "Pick a role.") Role role,
        UUID teamId,
        @NotBlank(message = "Use 8 to 128 characters.") @Size(min = 8, max = 128, message = "Use 8 to 128 characters.")
                String password) {

    CreateUserRequest {
        email = email == null ? null : email.strip();
        displayName = displayName == null ? null : displayName.strip();
    }
}
