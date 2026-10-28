package com.corridor.office.identity.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record RegistrationRequest(
        @NotBlank(message = "Use 2 to 80 characters.") @Size(min = 2, max = 80, message = "Use 2 to 80 characters.")
                String displayName,
        @NotBlank(message = "Enter your work email.")
                @Email(message = "Enter your work email.")
                @Size(max = 254, message = "Enter your work email.")
                String email,
        @NotBlank(message = "Use 8 to 128 characters.") @Size(min = 8, max = 128, message = "Use 8 to 128 characters.")
                String password) {

    RegistrationRequest {
        displayName = displayName == null ? null : displayName.strip();
        email = email == null ? null : email.strip();
    }
}
