package com.corridor.office.audit.api;

import com.corridor.office.audit.application.AuditEventView;
import com.corridor.office.audit.application.AuditFilter;
import com.corridor.office.audit.application.AuditQuery;
import com.corridor.office.audit.application.ChainVerification;
import com.corridor.office.common.api.CursorResponse;
import com.corridor.office.common.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit-events")
@Tag(name = "Audit log")
@SecurityRequirement(name = "bearerAuth")
class AuditController {

    private final AuditQuery query;

    AuditController(AuditQuery query) {
        this.query = query;
    }

    @GetMapping
    @Operation(summary = "List audit events, newest first (admins and HR)")
    @ApiResponse(responseCode = "200", description = "A slice of events; pass nextCursor to get the next one")
    @ApiResponse(responseCode = "400", description = "The cursor is broken or the time range is backwards")
    @ApiResponse(responseCode = "403", description = "Not an admin or HR")
    CursorResponse<AuditEventView> list(
            @AuthenticationPrincipal Jwt jwt,
            @ParameterObject AuditFilter filter,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int size) {
        return query.list(CurrentUser.from(jwt), filter, cursor, size);
    }

    @GetMapping("/verification")
    @Operation(summary = "Check that no audit event was changed or removed (admins and HR)")
    @ApiResponse(responseCode = "200", description = "Whether the log is intact, and the first altered event if not")
    @ApiResponse(responseCode = "403", description = "Not an admin or HR")
    ChainVerification verify(@AuthenticationPrincipal Jwt jwt) {
        return query.verify(CurrentUser.from(jwt));
    }
}
