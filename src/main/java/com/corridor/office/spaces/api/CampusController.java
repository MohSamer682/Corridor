package com.corridor.office.spaces.api;

import com.corridor.office.common.security.CurrentUser;
import com.corridor.office.spaces.application.CampusQuery;
import com.corridor.office.spaces.application.CampusView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Campus")
@SecurityRequirement(name = "bearerAuth")
class CampusController {

    private final CampusQuery campus;

    CampusController(CampusQuery campus) {
        this.campus = campus;
    }

    @GetMapping("/api/v1/campus")
    @Operation(summary = "Buildings of your company with their floors, in one call")
    @ApiResponse(responseCode = "200", description = "Buildings in campus order, floors from the ground up")
    CampusView campus(@AuthenticationPrincipal Jwt jwt) {
        return campus.campus(CurrentUser.from(jwt));
    }
}
