package com.corridor.office.identity.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record LoginRequest(
        @NotBlank(message = "Enter your work email.")
                @Email(message = "Enter your work email.")
                @Size(max = 254, message = "Enter your work email.")
                String email,
        @NotBlank(message = "Use 8 to 128 characters.") @Size(min = 8, max = 128, message = "Use 8 to 128 characters.")
                String password) {

    LoginRequest {
        email = email == null ? null : email.strip();
    }
}
