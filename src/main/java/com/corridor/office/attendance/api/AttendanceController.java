package com.corridor.office.attendance.api;

import com.corridor.office.attendance.application.AttendanceService;
import com.corridor.office.attendance.application.AttendanceService.PunchView;
import com.corridor.office.common.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Attendance")
@SecurityRequirement(name = "bearerAuth")
class AttendanceController {

    private static final String KEY = "Idempotency-Key";

    private final AttendanceService attendance;

    AttendanceController(AttendanceService attendance) {
        this.attendance = attendance;
    }

    @PostMapping("/api/v1/me/punch-in")
    @Operation(summary = "Punch in; the same Idempotency-Key always gets the first answer")
    @ApiResponse(responseCode = "200", description = "Punched in")
    @ApiResponse(responseCode = "400", description = "No Idempotency-Key, or not a UUID")
    @ApiResponse(responseCode = "409", description = "You're already punched in.")
    PunchView punchIn(@AuthenticationPrincipal Jwt jwt, @RequestHeader(KEY) UUID key) {
        return attendance.punchIn(CurrentUser.from(jwt), key);
    }

    @PostMapping("/api/v1/me/punch-out")
    @Operation(summary = "Punch out; the same Idempotency-Key always gets the first answer")
    @ApiResponse(responseCode = "200", description = "Punched out, with today's total")
    @ApiResponse(responseCode = "400", description = "No Idempotency-Key, or not a UUID")
    @ApiResponse(responseCode = "409", description = "You're not punched in.")
    PunchView punchOut(@AuthenticationPrincipal Jwt jwt, @RequestHeader(KEY) UUID key) {
        return attendance.punchOut(CurrentUser.from(jwt), key);
    }

    @GetMapping("/api/v1/me/punch")
    @Operation(summary = "Whether you're punched in, since when, and today's total so far")
    @ApiResponse(responseCode = "200", description = "Your punch clock")
    PunchView status(@AuthenticationPrincipal Jwt jwt) {
        return attendance.status(CurrentUser.from(jwt));
    }
}
