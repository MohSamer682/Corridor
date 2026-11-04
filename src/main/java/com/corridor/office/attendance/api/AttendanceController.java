package com.corridor.office.attendance.api;

import com.corridor.office.attendance.application.AttendanceQuery;
import com.corridor.office.attendance.application.AttendanceQuery.TeamDay;
import com.corridor.office.attendance.application.AttendanceService;
import com.corridor.office.attendance.application.AttendanceService.PunchView;
import com.corridor.office.attendance.domain.WorkDay;
import com.corridor.office.common.api.PageResponse;
import com.corridor.office.common.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Attendance")
@SecurityRequirement(name = "bearerAuth")
class AttendanceController {

    private static final String KEY = "Idempotency-Key";

    private final AttendanceService attendance;
    private final AttendanceQuery days;

    AttendanceController(AttendanceService attendance, AttendanceQuery days) {
        this.attendance = attendance;
        this.days = days;
    }

    @GetMapping("/api/v1/me/attendance")
    @Operation(summary = "Your work days between two dates, newest first (up to 62 days)")
    @ApiResponse(responseCode = "200", description = "One entry per day, with intervals and late minutes")
    List<WorkDay> mine(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return days.mine(CurrentUser.from(jwt), from, to);
    }

    @GetMapping("/api/v1/attendance")
    @Operation(summary = "Everyone's day: managers see their team, HR and admins the whole company")
    @ApiResponse(responseCode = "200", description = "A page of people with their day")
    @ApiResponse(responseCode = "403", description = "Employees, or a manager asking about another team")
    PageResponse<TeamDay> team(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day,
            @RequestParam(required = false) UUID teamId,
            @RequestParam(required = false, defaultValue = "") String query,
            @RequestParam(defaultValue = "0") int page) {
        return days.team(CurrentUser.from(jwt), day, teamId, query, Math.max(0, page));
    }

    @GetMapping("/api/v1/attendance/{userId}")
    @Operation(summary = "One person's work days and punches between two dates")
    @ApiResponse(responseCode = "200", description = "One entry per day")
    @ApiResponse(responseCode = "403", description = "Not you, not your team, and you're not HR or an admin")
    List<WorkDay> person(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return days.person(CurrentUser.from(jwt), userId, from, to);
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
