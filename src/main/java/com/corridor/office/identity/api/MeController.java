package com.corridor.office.identity.api;

import com.corridor.office.identity.application.ProfileQuery;
import com.corridor.office.identity.application.UserView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Profile")
class MeController {

    private final ProfileQuery profiles;

    MeController(ProfileQuery profiles) {
        this.profiles = profiles;
    }

    @GetMapping("/api/v1/me")
    @Operation(summary = "The signed-in user", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "The signed-in user")
    @ApiResponse(responseCode = "401", description = "No valid access token")
    UserView me(@AuthenticationPrincipal Jwt jwt) {
        return profiles.currentUser(UUID.fromString(jwt.getSubject()));
    }
}
