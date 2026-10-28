package com.corridor.office.identity.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

record TeamRequest(
        @NotBlank(message = "Use 2 to 60 characters.") @Size(min = 2, max = 60, message = "Use 2 to 60 characters.")
                String name,
        @NotBlank(message = "Pick a color.") @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Pick a color.")
                String color) {

    TeamRequest {
        name = name == null ? null : name.strip();
    }
}
