package com.corridor.office.insights.api;

import com.corridor.office.common.security.CurrentUser;
import com.corridor.office.insights.application.CommandCenter;
import com.corridor.office.insights.application.CommandCenterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Command Center")
@SecurityRequirement(name = "bearerAuth")
class CommandCenterController {

    private final CommandCenterService commandCenter;

    CommandCenterController(CommandCenterService commandCenter) {
        this.commandCenter = commandCenter;
    }

    @GetMapping("/api/v1/command-center")
    @Operation(summary = "Who is in, online, in meetings and in focus, room use and 14 days of attendance")
    @ApiResponse(responseCode = "200", description = "A manager's team, or the whole company for HR and admins")
    @ApiResponse(responseCode = "403", description = "Employees don't see the Command Center")
    CommandCenter snapshot(@AuthenticationPrincipal Jwt jwt) {
        return commandCenter.snapshot(CurrentUser.from(jwt));
    }
}
